package com.bium.domain.fridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.fridge.dto.FridgeNameRequest;
import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.dto.FridgeShoppingDayRequest;
import com.bium.domain.fridge.dto.FridgeVisibilityRequest;
import com.bium.domain.fridge.service.FridgeService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fridges")
@RequiredArgsConstructor
public class FridgeController {

	private final FridgeService fridgeService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<FridgeResponse>>> getMyFridges(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(ApiResponse.success(fridgeService.getMyFridges(Long.valueOf(jwt.getSubject()))));
	}

	// 속한 냉장고가 하나도 없을 때만 만들 수 있다
	@PostMapping
	public ResponseEntity<ApiResponse<FridgeResponse>> create(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody FridgeNameRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			fridgeService.create(Long.valueOf(jwt.getSubject()), request)));
	}

	@PatchMapping("/{fridgeId}/name")
	public ResponseEntity<ApiResponse<FridgeResponse>> rename(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @Valid @RequestBody FridgeNameRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeService.rename(Long.valueOf(jwt.getSubject()), fridgeId, request)));
	}

	@PatchMapping("/{fridgeId}/shopping-day")
	public ResponseEntity<ApiResponse<FridgeResponse>> changeShoppingDay(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @RequestBody FridgeShoppingDayRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeService.changeShoppingDay(Long.valueOf(jwt.getSubject()), fridgeId, request)));
	}

	@PatchMapping("/{fridgeId}/visibility")
	public ResponseEntity<ApiResponse<FridgeResponse>> changeVisibility(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @Valid @RequestBody FridgeVisibilityRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeService.changeVisibility(Long.valueOf(jwt.getSubject()), fridgeId, request)));
	}

}
