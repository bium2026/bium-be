package com.bium.domain.recipe.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.recipe.dto.CookingCandidatesResponse;
import com.bium.domain.recipe.dto.CookingLogResponse;
import com.bium.domain.recipe.dto.CookingRequest;
import com.bium.domain.recipe.dto.CookingUndoResponse;
import com.bium.domain.recipe.service.CookingService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CookingController {

	private final CookingService cookingService;

	@GetMapping("/api/recipes/{recipeId}/cooking-candidates")
	public ResponseEntity<ApiResponse<CookingCandidatesResponse>> getCandidates(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long recipeId) {
		return ResponseEntity.ok(ApiResponse.success(
			cookingService.getCandidates(Long.valueOf(jwt.getSubject()), recipeId)));
	}

	@PostMapping("/api/recipes/{recipeId}/cooking-logs")
	public ResponseEntity<ApiResponse<CookingLogResponse>> cook(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long recipeId, @Valid @RequestBody CookingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			cookingService.cook(Long.valueOf(jwt.getSubject()), recipeId, request)));
	}

	// 기록을 지우지 않고 되돌린 시각만 남겨서 DELETE 대신 POST로 둔다
	@PostMapping("/api/cooking-logs/{cookingLogId}/undo")
	public ResponseEntity<ApiResponse<CookingUndoResponse>> undo(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long cookingLogId) {
		return ResponseEntity.ok(ApiResponse.success(
			cookingService.undo(Long.valueOf(jwt.getSubject()), cookingLogId)));
	}

}
