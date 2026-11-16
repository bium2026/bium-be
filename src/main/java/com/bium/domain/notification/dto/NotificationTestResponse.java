package com.bium.domain.notification.dto;

// 테스트 메일을 보낸 주소와, 메일에 담은 D-3·D-day 재료 수
public record NotificationTestResponse(String sentTo, int itemCount) {
}
