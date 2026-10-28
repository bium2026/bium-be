package com.bium.domain.receipt.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bium.domain.receipt.dto.ReceiptScanResponse;
import com.bium.domain.receipt.service.ReceiptScanService;
import com.bium.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReceiptScanController {

	private final ReceiptScanService receiptScanService;

	// 읽은 후보만 돌려주고 저장하지 않아 200으로 응답한다. 사진이 없을 때도 같은 오류 형식을 주려고 서비스에서 확인한다
	@PostMapping(value = "/api/fridges/{fridgeId}/receipt-scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<ReceiptScanResponse>> scan(@AuthenticationPrincipal Jwt jwt,
		@PathVariable Long fridgeId, @RequestPart(required = false) MultipartFile image) {
		return ResponseEntity.ok(ApiResponse.success(
			receiptScanService.scan(Long.valueOf(jwt.getSubject()), fridgeId, image)));
	}

}
