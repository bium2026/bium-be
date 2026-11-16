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

	// 설정을 저장한 적이 없을 때 보여 주는 알림 시각
	static final LocalTime DEFAULT_NOTIFY_TIME = LocalTime.of(9, 0);

	private final NotificationSettingRepository settingRepository;
	private final UserRepository userRepository;

	// 저장한 적이 없으면 기본값(켜짐, 9시, D-3·D-day)을 저장하지 않고 보여 준다
	public NotificationSettingResponse get(Long userId) {
		User user = findUser(userId);
		return settingRepository.findById(userId)
			.map(setting -> toResponse(user, setting))
			.orElseGet(() -> new NotificationSettingResponse(user.getEmail(), true, DEFAULT_NOTIFY_TIME, true, true, null));
	}

	// 받을 주소가 없으면 알림을 켤 수 없다
	@Transactional
	public NotificationSettingResponse update(Long userId, NotificationSettingRequest request) {
		if (request.enabled() && request.email() == null) {
			throw new BusinessException(ErrorCode.NOTIFICATION_EMAIL_REQUIRED);
		}
		User user = findUser(userId);
		user.changeEmail(request.email());
		NotificationSetting setting = settingRepository.findById(userId)
			.orElseGet(() -> settingRepository.save(new NotificationSetting(user, request.notifyTime())));
		setting.update(request.enabled(), request.notifyTime(), request.notifyD3(), request.notifyDday());
		return toResponse(user, setting);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private NotificationSettingResponse toResponse(User user, NotificationSetting setting) {
		return new NotificationSettingResponse(user.getEmail(), setting.isEnabled(), setting.getNotifyTime(),
			setting.isNotifyD3(), setting.isNotifyDday(), setting.getLastSentDate());
	}

}
