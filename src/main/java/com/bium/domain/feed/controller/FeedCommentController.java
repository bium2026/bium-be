package com.bium.domain.feed.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.feed.dto.FeedCommentCreateRequest;
import com.bium.domain.feed.dto.FeedCommentPageResponse;
import com.bium.domain.feed.dto.FeedCommentResponse;
import com.bium.domain.feed.service.FeedCommentService;
import com.bium.global.response.ApiResponse;
import com.bium.global.security.LoginUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/feed/fridges/{fridgeId}/comments")
@RequiredArgsConstructor
public class FeedCommentController {

	private final FeedCommentService feedCommentService;

	@PostMapping
	public ResponseEntity<ApiResponse<FeedCommentResponse>> create(@LoginUserId Long userId,
		@PathVariable Long fridgeId, @Valid @RequestBody FeedCommentCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
			feedCommentService.create(userId, fridgeId, request)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<FeedCommentPageResponse>> getComments(@LoginUserId Long userId,
		@PathVariable Long fridgeId, @RequestParam(required = false) Long cursor) {
		return ResponseEntity.ok(ApiResponse.success(feedCommentService.getComments(userId, fridgeId, cursor)));
	}

	// 고칠 때도 쓸 때와 같은 조건으로 검사한다
	@PutMapping("/{commentId}")
	public ResponseEntity<ApiResponse<FeedCommentResponse>> update(@LoginUserId Long userId,
		@PathVariable Long fridgeId, @PathVariable Long commentId, @Valid @RequestBody FeedCommentCreateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
			feedCommentService.update(userId, fridgeId, commentId, request)));
	}

	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> delete(@LoginUserId Long userId, @PathVariable Long fridgeId,
		@PathVariable Long commentId) {
		feedCommentService.delete(userId, fridgeId, commentId);
		return ResponseEntity.noContent().build();
	}

}
