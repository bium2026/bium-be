package com.bium.domain.ingredient.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.dto.IngredientCatalogCreateRequest;
import com.bium.domain.ingredient.dto.IngredientCatalogListResponse;
import com.bium.domain.ingredient.dto.IngredientCatalogResponse;
import com.bium.domain.ingredient.service.IngredientCatalogService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ingredient-catalogs")
@RequiredArgsConstructor
public class IngredientCatalogController {

	private final IngredientCatalogService ingredientCatalogService;

	@GetMapping
	public ResponseEntity<ApiResponse<IngredientCatalogListResponse>> getMyCatalog(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(
			ingredientCatalogService.getMyCatalog(Long.valueOf(jwt.getSubject()))));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<IngredientCatalogResponse>> create(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody IngredientCatalogCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			ingredientCatalogService.create(Long.valueOf(jwt.getSubject()), request)));
	}

	// 프론트의 공통 요청 함수는 응답 본문을 읽어 성공 여부를 판단하므로 204 대신 공통 응답 형식으로 돌려준다
	@DeleteMapping("/{catalogId}")
	public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long catalogId) {
		ingredientCatalogService.delete(Long.valueOf(jwt.getSubject()), catalogId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

}
