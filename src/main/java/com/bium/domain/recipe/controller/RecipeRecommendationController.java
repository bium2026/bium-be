package com.bium.domain.recipe.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.recipe.dto.RecipeRecommendationRequest;
import com.bium.domain.recipe.dto.RecipeRecommendationResponse;
import com.bium.domain.recipe.recommendation.RecommendationRateLimiter;
import com.bium.domain.recipe.service.RecipeRecommendationService;
import com.bium.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeRecommendationController {

	private final RecipeRecommendationService recommendationService;
	private final RecommendationRateLimiter rateLimiter;

	// JSON을 받을 수 없는 요청은 Gemini를 호출하기 전에 거절한다
	@PostMapping(value = "/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<RecipeRecommendationResponse>> recommend(
		@Valid @RequestBody RecipeRecommendationRequest request, HttpServletRequest servletRequest) {
		// 로그인을 붙이기 전까지 IP로 호출 수를 센다
		rateLimiter.check(servletRequest.getRemoteAddr());
		RecipeRecommendationResponse response = RecipeRecommendationResponse.from(
			recommendationService.recommend(request.toIngredients(), request.isPrioritizeUrgent()));
		return ResponseEntity.ok(ApiResponse.success(response));
	}

}
