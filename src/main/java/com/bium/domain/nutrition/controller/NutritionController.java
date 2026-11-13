package com.bium.domain.nutrition.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.nutrition.dto.NutritionResponse;
import com.bium.domain.nutrition.dto.NutritionSummaryResponse;
import com.bium.domain.nutrition.service.NutritionService;
import com.bium.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NutritionController {

	private final NutritionService nutritionService;

	// 재료 수정 모달을 열 때 부른다. 맞는 식품이 없으면 data를 null로 준다
	@GetMapping("/api/fridge-items/{itemId}/nutrition")
	public ResponseEntity<ApiResponse<NutritionResponse>> getItemNutrition(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long itemId) {
		return ResponseEntity.ok(ApiResponse.success(
			nutritionService.getItemNutrition(Long.valueOf(jwt.getSubject()), itemId)));
	}

	// storage를 빼면 냉장고 전체, 주면 그 구역(FRIDGE·FREEZER·ROOM)만 더한다
	@GetMapping("/api/fridges/{fridgeId}/nutrition-summary")
	public ResponseEntity<ApiResponse<NutritionSummaryResponse>> getFridgeSummary(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @RequestParam(required = false) StorageType storage) {
		return ResponseEntity.ok(ApiResponse.success(
			nutritionService.getFridgeSummary(Long.valueOf(jwt.getSubject()), fridgeId, storage)));
	}

}
