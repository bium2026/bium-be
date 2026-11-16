package com.bium.domain.fridge.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.fridge.dto.FridgeMemberResponse;
import com.bium.domain.fridge.service.FridgeMemberService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/members")
@RequiredArgsConstructor
public class FridgeMemberController {

	private final FridgeMemberService fridgeMemberService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<FridgeMemberResponse>>> getMembers(@LoginUserId Long userId,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(
			fridgeMemberService.getMembers(userId, fridgeId)));
	}

	// 글자 그대로인 주소가 {userId}보다 먼저 맞춰지므로 me가 사용자 id로 읽히지 않는다
	@DeleteMapping("/me")
	public ResponseEntity<Void> leave(@LoginUserId Long userId, @PathVariable Long fridgeId) {
		fridgeMemberService.leave(userId, fridgeId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<Void> remove(@LoginUserId Long userId, @PathVariable Long fridgeId,
		@PathVariable("userId") Long targetUserId) {
		fridgeMemberService.remove(userId, fridgeId, targetUserId);
		return ResponseEntity.noContent().build();
	}

}
