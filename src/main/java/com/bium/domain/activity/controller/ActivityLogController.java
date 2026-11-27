package com.bium.domain.activity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.activity.dto.ActivityLogPageResponse;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ActivityLogController {

	private final ActivityLogService activityLogService;

	// 최근 기록부터 50개씩 준다. 요리 하나로 남긴 기록이 페이지 사이에서 나뉘지 않게 50개를 넘길 수 있다
	@GetMapping("/api/fridges/{fridgeId}/activities")
	public ResponseEntity<ApiResponse<ActivityLogPageResponse>> getPage(@LoginUserId Long userId,
		@PathVariable Long fridgeId, @RequestParam(required = false) Long cursor) {
		return ResponseEntity.ok(ApiResponse.success(
			activityLogService.getPage(userId, fridgeId, cursor)));
	}

}
