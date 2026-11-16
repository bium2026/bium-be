package com.bium.domain.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.notification.dto.NotificationSettingRequest;
import com.bium.domain.notification.dto.NotificationSettingResponse;
import com.bium.domain.notification.service.NotificationSettingService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {

	private final NotificationSettingService settingService;

	@GetMapping
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> get(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(settingService.get(Long.valueOf(jwt.getSubject()))));
	}

	@PutMapping
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> update(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody NotificationSettingRequest request) {
		return ResponseEntity.ok(ApiResponse.success(settingService.update(Long.valueOf(jwt.getSubject()), request)));
	}

}
