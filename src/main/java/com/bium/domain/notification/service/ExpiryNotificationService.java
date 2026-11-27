package com.bium.domain.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.notification.dto.NotificationTestResponse;
import com.bium.domain.notification.entity.NotificationLog;
import com.bium.domain.notification.entity.NotificationSetting;
import com.bium.domain.notification.entity.NotificationStatus;
import com.bium.domain.notification.repository.NotificationLogRepository;
import com.bium.domain.notification.repository.NotificationSettingRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 유통기한이 오늘(D-day)이거나 3일 남은(D-3) 재료를 이메일로 알린다.
 * 메일은 느리고 실패할 수 있어 트랜잭션 밖에서 보내고, 보낼 차례 차지·재료 조회·기록은 각각 짧은 트랜잭션으로 한다.
 * 차례를 잡은 날(lastCheckedDate)과 메일을 실제로 보낸 날(lastSentDate)을 따로 적어, 보내지 않은 날은 보낸 날로 보이지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryNotificationService {

	private static final int D3_DAYS = 3;

	private final NotificationSettingRepository settingRepository;
	private final NotificationLogRepository logRepository;
	private final FridgeItemRepository fridgeItemRepository;
	private final UserRepository userRepository;
	private final ExpiryMailComposer composer;
	private final ExpiryMailSender mailSender;
	private final TransactionTemplate transactionTemplate;

	// 예약 작업이 부른다. 알림 시각이 지났고 오늘 아직 보내지 않은 사용자에게 보내고, 보낸 메일 수를 돌려준다
	public int sendDue(LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		List<NotificationSetting> due = transactionTemplate.execute(status -> settingRepository.findDue(now.toLocalTime(), today));
		int sent = 0;
		for (NotificationSetting setting : due) {
			Long userId = setting.getUserId();
			// 다른 실행이 먼저 차지했으면 넘어간다. 실패해도 오늘은 다시 보내지 않아 SMTP 장애 때 같은 실패가 쌓이지 않는다
			Integer claimed = transactionTemplate.execute(status -> settingRepository.claimToday(userId, today));
			if (claimed == null || claimed == 0) {
				continue;
			}
			List<FridgeItem> items = findItems(userId, dates(setting, today));
			// 알릴 재료가 없는 날은 메일을 보내지 않는다
			if (items.isEmpty()) {
				continue;
			}
			User user = setting.getUser();
			if (send(user, setting.getEmail(), composer.subject(items.size()),
				composer.body(user.getNickname(), items, today), items.size())) {
				transactionTemplate.executeWithoutResult(status -> settingRepository.markSent(userId, today));
				sent++;
			}
		}
		return sent;
	}

	// 알림 설정 화면의 '테스트 메일 보내기'. 오늘 알림을 보냈는지와 상관없이 지금 기준 재료로 바로 보낸다.
	// 설정을 저장한 적이 없으면 화면에 기본값으로 보이는 로그인 이메일로 보낸다
	public NotificationTestResponse sendTest(Long userId, LocalDate today) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		// 저장한 설정의 주소가 비어 있으면(알림을 끄고 지운 경우) 로그인 이메일로 보내지 않는다
		Optional<NotificationSetting> setting = settingRepository.findById(userId);
		String email = setting.isPresent() ? setting.get().getEmail() : user.getEmail();
		if (email == null) {
			throw new BusinessException(ErrorCode.NOTIFICATION_EMAIL_REQUIRED);
		}
		List<FridgeItem> items = findItems(userId, List.of(today, today.plusDays(D3_DAYS)));
		if (!send(user, email, composer.testSubject(), composer.testBody(user.getNickname(), items, today), items.size())) {
			throw new BusinessException(ErrorCode.NOTIFICATION_MAIL_FAILED);
		}
		return new NotificationTestResponse(email, items.size());
	}

	private List<LocalDate> dates(NotificationSetting setting, LocalDate today) {
		List<LocalDate> dates = new ArrayList<>();
		if (setting.isNotifyDday()) {
			dates.add(today);
		}
		if (setting.isNotifyD3()) {
			dates.add(today.plusDays(D3_DAYS));
		}
		return dates;
	}

	private List<FridgeItem> findItems(Long userId, List<LocalDate> dates) {
		if (dates.isEmpty()) {
			return List.of();
		}
		return transactionTemplate.execute(status -> fridgeItemRepository.findExpiringForUser(userId, dates));
	}

	// 보낸 결과를 성공·실패 모두 기록한다
	private boolean send(User user, String email, String subject, String text, int itemCount) {
		try {
			mailSender.send(email, subject, text);
			transactionTemplate.executeWithoutResult(status ->
				logRepository.save(new NotificationLog(user, itemCount, NotificationStatus.SUCCESS, null)));
			return true;
		} catch (MailException e) {
			log.warn("유통기한 알림 메일을 보내지 못했습니다. userId={}", user.getId(), e);
			transactionTemplate.executeWithoutResult(status ->
				logRepository.save(new NotificationLog(user, itemCount, NotificationStatus.FAIL, e.getMessage())));
			return false;
		}
	}

}
