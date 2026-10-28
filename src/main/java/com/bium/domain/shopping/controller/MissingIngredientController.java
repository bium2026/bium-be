package com.bium.domain.shopping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.shopping.dto.MissingIngredientAddRequest;
import com.bium.domain.shopping.dto.ShoppingListItemListResponse;
import com.bium.domain.shopping.service.MissingIngredientService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MissingIngredientController {

	private final MissingIngredientService missingIngredientService;

	// 새로 담은 재료만 돌려준다. 이미 담겨 있어 건너뛴 재료는 응답에 없다
	@PostMapping("/api/recipes/{recipeId}/shopping-list-items")
	public ResponseEntity<ApiResponse<ShoppingListItemListResponse>> add(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long recipeId, @Valid @RequestBody MissingIngredientAddRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			missingIngredientService.add(Long.valueOf(jwt.getSubject()), recipeId, request)));
	}

}
