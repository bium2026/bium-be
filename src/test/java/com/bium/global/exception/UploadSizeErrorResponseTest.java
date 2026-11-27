package com.bium.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.bium.global.response.ApiResponse;

/**
 * 파일이 spring.servlet.multipart의 한도(10MB)를 넘으면 컨트롤러에 닿기 전에 막혀, 받은 주소에 맞는 사진 한도로 알린다.
 * MockMvc는 multipart 크기 제한을 확인하지 않아서 그때 나는 예외를 공통 예외 처리에 직접 넘긴다.
 */
class UploadSizeErrorResponseTest {

	private static final long MULTIPART_LIMIT = 10L * 1024 * 1024;

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void 도감_사진이_한도를_넘으면_도감_사진의_5MB_한도로_알린다() throws Exception {
		ResponseEntity<Object> response = handle("PUT", "/api/ingredient-catalogs/1/image");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody()).isEqualTo(ApiResponse.error(ErrorCode.INGREDIENT_IMAGE_TOO_LARGE));
		assertThat(ErrorCode.INGREDIENT_IMAGE_TOO_LARGE.getMessage()).contains("5MB");
	}

	@Test
	void 영수증_사진이_한도를_넘으면_영수증의_10MB_한도로_알린다() throws Exception {
		ResponseEntity<Object> response = handle("POST", "/api/fridges/1/receipt-scans");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody()).isEqualTo(ApiResponse.error(ErrorCode.IMAGE_TOO_LARGE));
		assertThat(ErrorCode.IMAGE_TOO_LARGE.getMessage()).contains("10MB");
	}

	private ResponseEntity<Object> handle(String method, String uri) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		return handler.handleException(new MaxUploadSizeExceededException(MULTIPART_LIMIT),
			new ServletWebRequest(request));
	}

}
