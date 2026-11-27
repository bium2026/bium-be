package com.bium.domain.notification.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

// email이 없으면 알림을 켜 두어도 보내지 않는다. lastSentDate는 예약 알림 메일을 실제로 보낸 마지막 날이다(테스트 메일은 빼고)
public record NotificationSettingResponse(
	String email,
	boolean enabled,
	@JsonFormat(pattern = "HH:mm")
	LocalTime notifyTime,
	boolean notifyD3,
	boolean notifyDday,
	LocalDate lastSentDate
) {
}
