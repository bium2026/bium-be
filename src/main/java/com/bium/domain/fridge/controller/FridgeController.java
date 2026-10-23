package com.bium.domain.fridge.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.service.FridgeService;
import com.bium.global.response.ApiResponse;

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

}
