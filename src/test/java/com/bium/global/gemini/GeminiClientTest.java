package com.bium.global.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

class GeminiClientTest {

	private static final String BASE_URL = "https://gemini.test";
	private static final String ENDPOINT = BASE_URL + "/v1beta/models/gemini-test:generateContent";

	private MockRestServiceServer server;
	private GeminiClient geminiClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		geminiClient = new GeminiClient(builder.build(), properties("test-key"));
	}

	@Test
	void 응답의_텍스트_조각을_이어서_돌려준다() {
		server.expect(requestTo(ENDPOINT))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-key"))
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("프롬프트"))
			.andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
			.andRespond(withSuccess("""
				{"candidates": [{"content": {"parts": [{"text": "{\\"recipes\\":"}, {"text": "[]}"}]}}]}
				""", MediaType.APPLICATION_JSON));

		assertThat(geminiClient.generateJson("프롬프트")).isEqualTo("{\"recipes\":\n[]}");
		server.verify();
	}

	@Test
	void 한도_초과_응답은_AI_한도_초과로_처리한다() {
		server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> geminiClient.generateJson("프롬프트"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
	}

	@Test
	void 서버_오류_응답은_AI_요청_실패로_처리한다() {
		server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

		assertThatThrownBy(() -> geminiClient.generateJson("프롬프트"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.AI_REQUEST_FAILED);
	}

	@Test
	void API_키가_없으면_호출하지_않고_실패한다() {
		GeminiClient clientWithoutKey = new GeminiClient(RestClient.create(BASE_URL), properties(""));

		assertThatThrownBy(() -> clientWithoutKey.generateJson("프롬프트"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.AI_REQUEST_FAILED);
	}

	private GeminiProperties properties(String apiKey) {
		return new GeminiProperties(apiKey, "gemini-test", BASE_URL, 0.55, Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

}
