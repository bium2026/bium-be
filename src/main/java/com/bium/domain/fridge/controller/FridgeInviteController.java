package com.bium.domain.fridge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.fridge.dto.FridgeInviteResponse;
import com.bium.domain.fridge.dto.FridgeJoinRequest;
import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.service.FridgeInviteService;
import com.bium.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fridges")
@RequiredArgsConstructor
public class FridgeInviteController {

	private final FridgeInviteService fridgeInviteService;

	@PostMapping("/{fridgeId}/invites")
	public ResponseEntity<ApiResponse<FridgeInviteResponse>> create(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			fridgeInviteService.create(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

	// 쓸 수 있는 코드가 없으면 data를 null로 준다
	@GetMapping("/{fridgeId}/invites/active")
	public ResponseEntity<ApiResponse<FridgeInviteResponse>> getActive(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeInviteService.getActive(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<FridgeResponse>> join(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody FridgeJoinRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeInviteService.join(Long.valueOf(jwt.getSubject()), request)));
	}

}
