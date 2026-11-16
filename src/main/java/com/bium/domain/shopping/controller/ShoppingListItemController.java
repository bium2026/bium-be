package com.bium.domain.shopping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.shopping.dto.ShoppingListItemCheckRequest;
import com.bium.domain.shopping.dto.ShoppingListItemCreateRequest;
import com.bium.domain.shopping.dto.ShoppingListItemListResponse;
import com.bium.domain.shopping.dto.ShoppingListItemResponse;
import com.bium.domain.shopping.service.ShoppingListItemService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 조회·담기는 냉장고 아래 주소를, 체크·빼기는 장보기 항목 주소를 써서 메서드마다 전체 주소를 적는다
@RestController
@RequiredArgsConstructor
public class ShoppingListItemController {

	private final ShoppingListItemService shoppingListItemService;

	@PostMapping("/api/fridges/{fridgeId}/shopping-list-items")
	public ResponseEntity<ApiResponse<ShoppingListItemResponse>> create(@LoginUserId Long userId,
		@PathVariable Long fridgeId, @Valid @RequestBody ShoppingListItemCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			shoppingListItemService.create(userId, fridgeId, request)));
	}

	@GetMapping("/api/fridges/{fridgeId}/shopping-list-items")
	public ResponseEntity<ApiResponse<ShoppingListItemListResponse>> getItems(@LoginUserId Long userId,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			shoppingListItemService.getItems(userId, fridgeId)));
	}

	@PatchMapping("/api/shopping-list-items/{itemId}")
	public ResponseEntity<ApiResponse<ShoppingListItemResponse>> check(@LoginUserId Long userId,
		@PathVariable Long itemId, @Valid @RequestBody ShoppingListItemCheckRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			shoppingListItemService.check(userId, itemId, request)));
	}

	@DeleteMapping("/api/shopping-list-items/{itemId}")
	public ResponseEntity<Void> delete(@LoginUserId Long userId, @PathVariable Long itemId) {
		shoppingListItemService.delete(userId, itemId);
		return ResponseEntity.noContent().build();
	}

}
