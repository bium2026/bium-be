package com.bium.domain.shopping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.shopping.dto.PurchasedItemCreateRequest;
import com.bium.domain.shopping.dto.PurchasedItemListResponse;
import com.bium.domain.shopping.dto.PurchasedItemMoveRequest;
import com.bium.domain.shopping.dto.PurchasedItemResponse;
import com.bium.domain.shopping.service.PurchasedItemService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PurchasedItemController {

	private final PurchasedItemService purchasedItemService;

	@PostMapping("/api/fridges/{fridgeId}/purchased-items")
	public ResponseEntity<ApiResponse<PurchasedItemResponse>> create(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @Valid @RequestBody PurchasedItemCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			purchasedItemService.create(Long.valueOf(jwt.getSubject()), fridgeId, request)));
	}

	@GetMapping("/api/fridges/{fridgeId}/purchased-items")
	public ResponseEntity<ApiResponse<PurchasedItemListResponse>> getItems(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			purchasedItemService.getItems(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

	@PostMapping("/api/purchased-items/{purchasedItemId}/move")
	public ResponseEntity<ApiResponse<FridgeItemResponse>> moveToFridge(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long purchasedItemId, @Valid @RequestBody PurchasedItemMoveRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			purchasedItemService.moveToFridge(Long.valueOf(jwt.getSubject()), purchasedItemId, request)));
	}

	// 프론트의 공통 요청 함수는 응답 본문을 읽어 성공 여부를 판단하므로 204 대신 공통 응답 형식으로 돌려준다
	@DeleteMapping("/api/purchased-items/{purchasedItemId}")
	public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long purchasedItemId) {
		purchasedItemService.delete(Long.valueOf(jwt.getSubject()), purchasedItemId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

}
