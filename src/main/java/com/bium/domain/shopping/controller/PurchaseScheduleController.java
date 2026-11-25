package com.bium.domain.shopping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.shopping.dto.PurchaseScheduleResponse;
import com.bium.domain.shopping.service.PurchaseScheduleService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PurchaseScheduleController {

	private final PurchaseScheduleService purchaseScheduleService;

	// 장보기 목록 화면의 달력에 재료별 다음 구매일을 보여 줄 때 부른다
	@GetMapping("/api/fridges/{fridgeId}/purchase-schedule")
	public ResponseEntity<ApiResponse<PurchaseScheduleResponse>> getSchedule(@LoginUserId Long userId,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(purchaseScheduleService.getSchedule(userId, fridgeId)));
	}

}
