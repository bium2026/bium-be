package com.bium.domain.notification.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

// email이 없으면 알림을 켜 두어도 보내지 않는다. lastSentDate는 마지막으로 알림을 보낸 날이다
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
