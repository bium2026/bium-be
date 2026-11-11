package com.bium.domain.nutrition.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.nutrition.dto.NutritionResponse;
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

}
