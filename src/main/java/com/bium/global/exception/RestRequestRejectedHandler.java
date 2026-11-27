package com.bium.global.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

import com.bium.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

// 주소에 //나 ..이 들어간 요청은 시큐리티 방화벽(StrictHttpFirewall)이 컨트롤러에 닿기 전에 막아 @RestControllerAdvice까지
// 오지 않는다. 기본 처리는 스프링 기본 오류 JSON을 주므로, 다른 잘못된 요청과 같은 공통 응답 형식으로 보낸다
@Slf4j
@Component
@RequiredArgsConstructor
public class RestRequestRejectedHandler implements RequestRejectedHandler {

	private final JsonMapper jsonMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		RequestRejectedException requestRejectedException) throws IOException {
		log.debug("방화벽이 요청을 막았습니다: {}", requestRejectedException.getMessage());
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getWriter(),
			ApiResponse.error(ErrorCode.INVALID_INPUT.name(), "요청 형식이 올바르지 않습니다."));
	}

}
