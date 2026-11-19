package com.bium.domain.notification.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.notification.dto.NotificationSettingRequest;
import com.bium.domain.notification.dto.NotificationSettingResponse;
import com.bium.domain.notification.dto.NotificationTestResponse;
import com.bium.domain.notification.service.ExpiryNotificationService;
import com.bium.domain.notification.service.NotificationSettingService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {

	private final NotificationSettingService settingService;
	private final ExpiryNotificationService notificationService;
	private final Clock clock;

	@GetMapping
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> get(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(settingService.get(Long.valueOf(jwt.getSubject()))));
	}

	@PutMapping
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> update(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody NotificationSettingRequest request) {
		return ResponseEntity.ok(ApiResponse.success(settingService.update(Long.valueOf(jwt.getSubject()), request)));
	}

	// 저장한 주소로 지금 기준 D-3·D-day 재료를 담은 테스트 메일을 바로 보낸다
	@PostMapping("/test-mail")
	public ResponseEntity<ApiResponse<NotificationTestResponse>> sendTest(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(
			notificationService.sendTest(Long.valueOf(jwt.getSubject()), LocalDate.now(clock))));
	}

}
