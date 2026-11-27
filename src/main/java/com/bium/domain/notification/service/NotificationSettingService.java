package com.bium.domain.notification.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.notification.dto.NotificationSettingRequest;
import com.bium.domain.notification.dto.NotificationSettingResponse;
import com.bium.domain.notification.entity.NotificationSetting;
import com.bium.domain.notification.repository.NotificationSettingRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

	// 기본 알림 설정의 알림 시각
	public static final LocalTime DEFAULT_NOTIFY_TIME = LocalTime.of(9, 0);

	private final NotificationSettingRepository settingRepository;
	private final UserRepository userRepository;

	// 설정 줄이 없으면(기본 설정을 만들기 전) 기본값(로그인 이메일, 켜짐, 9시, D-3·D-day)을 보여 준다
	public NotificationSettingResponse get(Long userId) {
		return settingRepository.findById(userId)
			.map(this::toResponse)
			.orElseGet(() -> new NotificationSettingResponse(findUser(userId).getEmail(), true, DEFAULT_NOTIFY_TIME,
				true, true, null));
	}

	// 가입할 때 화면이 보여 주는 기본값 그대로 저장해, 설정을 한 번도 저장하지 않아도 알림 메일이 간다
	@Transactional
	public void createDefault(User user) {
		NotificationSetting setting = new NotificationSetting(user, DEFAULT_NOTIFY_TIME);
		setting.changeEmail(user.getEmail());
		settingRepository.save(setting);
	}

	// 받을 주소가 없으면 알림을 켤 수 없다
	@Transactional
	public NotificationSettingResponse update(Long userId, NotificationSettingRequest request) {
		if (request.enabled() && request.email() == null) {
			throw new BusinessException(ErrorCode.NOTIFICATION_EMAIL_REQUIRED);
		}
		NotificationSetting setting = settingRepository.findById(userId)
			.orElseGet(() -> settingRepository.save(new NotificationSetting(findUser(userId), request.notifyTime())));
		setting.changeEmail(request.email());
		setting.update(request.enabled(), request.notifyTime(), request.notifyD3(), request.notifyDday());
		return toResponse(setting);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private NotificationSettingResponse toResponse(NotificationSetting setting) {
		return new NotificationSettingResponse(setting.getEmail(), setting.isEnabled(), setting.getNotifyTime(),
			setting.isNotifyD3(), setting.isNotifyDday(), setting.getLastSentDate());
	}

}
