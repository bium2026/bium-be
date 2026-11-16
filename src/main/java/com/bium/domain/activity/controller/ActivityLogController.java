package com.bium.domain.activity.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.activity.dto.ActivityLogResponse;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ActivityLogController {

	private final ActivityLogService activityLogService;

	// 최근 기록부터 50개까지 준다
	@GetMapping("/api/fridges/{fridgeId}/activities")
	public ResponseEntity<ApiResponse<List<ActivityLogResponse>>> getRecent(@LoginUserId Long userId,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			activityLogService.getRecent(userId, fridgeId)));
	}

}
