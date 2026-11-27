package com.bium.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 주소에 //가 들어가면 스프링 시큐리티의 방화벽(StrictHttpFirewall)이 컨트롤러에 닿기 전에 막는다.
 * 이때도 공통 응답 형식으로 알리는지 실제 서버로 보낸다. MockMvc는 서블릿 컨테이너의 주소 처리를 거치지 않아 막히지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestRejectedResponseTest {

	@Value("${local.server.port}")
	private int port;

	@Test
	void 주소에_슬래시가_겹치면_INVALID_INPUT과_한국어로_알린다() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/fridges//items")).GET().build(),
			HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body())
			.contains("\"success\":false")
			.contains("\"code\":\"INVALID_INPUT\"")
			.contains("\"message\":\"요청 형식이 올바르지 않습니다.\"");
	}

}
