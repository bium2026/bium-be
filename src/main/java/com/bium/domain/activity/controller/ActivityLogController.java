package com.bium.domain.activity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.activity.dto.ActivityLogResponse;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ActivityLogController {

	private final ActivityLogService activityLogService;

	// 최근 기록부터 50개까지 준다
	@GetMapping("/api/fridges/{fridgeId}/activities")
	public ResponseEntity<ApiResponse<List<ActivityLogResponse>>> getRecent(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			activityLogService.getRecent(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

}
