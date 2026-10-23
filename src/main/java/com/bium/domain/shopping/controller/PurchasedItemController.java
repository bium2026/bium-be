package com.bium.domain.shopping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.shopping.dto.PurchasedItemCreateRequest;
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

}
