package com.bium.domain.feed.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.feed.dto.FeedFridgeDetailResponse;
import com.bium.domain.feed.dto.FeedPageResponse;
import com.bium.domain.feed.service.FeedService;
import com.bium.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/feed/fridges")
@RequiredArgsConstructor
public class FeedController {

	private final FeedService feedService;

	// saved=true면 내가 저장한 냉장고만 준다
	@GetMapping
	public ResponseEntity<ApiResponse<FeedPageResponse>> getFeed(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "false") boolean saved) {
		return ResponseEntity.ok(ApiResponse.success(
			feedService.getFeed(Long.valueOf(jwt.getSubject()), cursor, saved)));
	}

	@GetMapping("/{fridgeId}")
	public ResponseEntity<ApiResponse<FeedFridgeDetailResponse>> getFridge(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId) {
		return ResponseEntity.ok(ApiResponse.success(feedService.getFridge(Long.valueOf(jwt.getSubject()), fridgeId)));
	}

}
