package com.bium.domain.recipe.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.recipe.dto.RecipeRecommendationRequest;
import com.bium.domain.recipe.dto.RecipeRecommendationResponse;
import com.bium.domain.recipe.dto.RecipeResponse;
import com.bium.domain.recipe.service.RecipeService;
import com.bium.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RecipeController {

	private final RecipeService recipeService;

	// JSON을 받을 수 없는 요청은 Gemini를 호출하기 전에 거절한다
	@PostMapping(value = "/api/fridges/{fridgeId}/recipes/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<RecipeRecommendationResponse>> recommend(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @RequestBody(required = false) RecipeRecommendationRequest request) {
		boolean prioritizeUrgent = request != null && request.isPrioritizeUrgent();
		return ResponseEntity.ok(ApiResponse.success(
			recipeService.recommend(Long.valueOf(jwt.getSubject()), fridgeId, prioritizeUrgent)));
	}

	@GetMapping("/api/recipes/{recipeId}")
	public ResponseEntity<ApiResponse<RecipeResponse>> getRecipe(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long recipeId) {
		return ResponseEntity.ok(ApiResponse.success(recipeService.getRecipe(Long.valueOf(jwt.getSubject()), recipeId)));
	}

}
