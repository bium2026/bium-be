package com.bium.domain.ingredient.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.dto.FridgeItemCreateRequest;
import com.bium.domain.ingredient.dto.FridgeContentsResponse;
import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.ingredient.dto.FridgeItemUpdateRequest;
import com.bium.domain.ingredient.service.FridgeItemService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 추가는 냉장고 아래 주소를, 수정·삭제는 재료 주소를 써서 메서드마다 전체 주소를 적는다
@RestController
@RequiredArgsConstructor
public class FridgeItemController {

	private final FridgeItemService fridgeItemService;

	@PostMapping("/api/fridges/{fridgeId}/items")
	public ResponseEntity<ApiResponse<FridgeItemResponse>> create(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @Valid @RequestBody FridgeItemCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			fridgeItemService.create(Long.valueOf(jwt.getSubject()), fridgeId, request)));
    
	@GetMapping("/api/fridges/{fridgeId}/items")
	public ResponseEntity<ApiResponse<FridgeContentsResponse>> getContents(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeItemService.getContents(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

	// 모달의 세 칸을 모두 보내 통째로 바꾼다
	@PutMapping("/api/fridge-items/{itemId}")
	public ResponseEntity<ApiResponse<FridgeItemResponse>> update(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long itemId, @Valid @RequestBody FridgeItemUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeItemService.update(Long.valueOf(jwt.getSubject()), itemId, request)));
	}

	@DeleteMapping("/api/fridge-items/{itemId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
		@RequestParam Integer version) {
		fridgeItemService.delete(Long.valueOf(jwt.getSubject()), itemId, version);
		return ResponseEntity.noContent().build();
	}

}
