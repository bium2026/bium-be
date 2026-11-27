package com.bium.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.user.dto.MyProfileResponse;
import com.bium.domain.user.dto.NicknameChangeRequest;
import com.bium.domain.user.service.UserService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public ResponseEntity<ApiResponse<MyProfileResponse>> getMe(@LoginUserId Long userId) {
		return ResponseEntity.ok(ApiResponse.success(userService.getMe(userId)));
	}

	@PatchMapping("/nickname")
	public ResponseEntity<ApiResponse<MyProfileResponse>> changeNickname(@LoginUserId Long userId,
		@Valid @RequestBody NicknameChangeRequest request) {
		return ResponseEntity.ok(ApiResponse.success(userService.changeNickname(userId, request)));
	}

}
