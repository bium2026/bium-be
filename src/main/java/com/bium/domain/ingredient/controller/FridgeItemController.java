package com.bium.domain.ingredient.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.ingredient.dto.FridgeItemUpdateRequest;
import com.bium.domain.ingredient.service.FridgeItemService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fridge-items")
@RequiredArgsConstructor
public class FridgeItemController {

	private final FridgeItemService fridgeItemService;

	// 모달의 세 칸을 모두 보내 통째로 바꾼다
	@PutMapping("/{itemId}")
	public ResponseEntity<ApiResponse<FridgeItemResponse>> update(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long itemId, @Valid @RequestBody FridgeItemUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeItemService.update(Long.valueOf(jwt.getSubject()), itemId, request)));
	}

	@DeleteMapping("/{itemId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
		@RequestParam Integer version) {
		fridgeItemService.delete(Long.valueOf(jwt.getSubject()), itemId, version);
		return ResponseEntity.noContent().build();
	}

}
