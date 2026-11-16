package com.bium.domain.notification.dto;

import java.time.LocalTime;

import com.bium.global.util.SpaceTrimmer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 알림 설정 화면에서 저장한 값. email을 비우면 받을 주소를 지우며, 이때는 알림을 켤 수 없다.
 * notifyTime은 "HH:mm" 형식이다.
 */
public record NotificationSettingRequest(
	@Size(max = 255, message = "이메일이 너무 깁니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	String email,
	@NotNull(message = "알림을 켤지 보내 주세요.")
	Boolean enabled,
	@NotNull(message = "알림을 받을 시각을 선택해 주세요.")
	LocalTime notifyTime,
	@NotNull(message = "D-3 알림을 받을지 보내 주세요.")
	Boolean notifyD3,
	@NotNull(message = "D-day 알림을 받을지 보내 주세요.")
	Boolean notifyDday
) {

	public NotificationSettingRequest {
		email = SpaceTrimmer.trim(email);
		if (email != null && email.isEmpty()) {
			email = null;
		}
	}

}
