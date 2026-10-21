package com.bium.global.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.bium.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

// 토큰이 없거나 만료·위조된 요청은 시큐리티 필터에서 막혀 @RestControllerAdvice까지 오지 않으므로 여기서 공통 응답 형식으로 보낸다
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
	}

}
