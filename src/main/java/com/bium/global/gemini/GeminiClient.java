package com.bium.global.gemini;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

	private static final String API_KEY_HEADER = "x-goog-api-key";

	private final RestClient restClient;
	private final GeminiProperties properties;

	/**
	 * 프롬프트를 보내고 JSON 응답 모드로 받은 본문 텍스트를 돌려준다.
	 */
	public String generateJson(String prompt) {
		return generateJson(List.of(Map.of("text", prompt)), properties.temperature());
	}

	/**
	 * 사진과 프롬프트를 함께 보낸다. 사진 속 글자를 읽는 작업은 설정값보다 낮은 temperature로 부른다.
	 */
	public String generateJson(String prompt, byte[] image, String mimeType, double temperature) {
		Map<String, Object> imagePart = Map.of("inlineData", Map.of(
			"mimeType", mimeType,
			"data", Base64.getEncoder().encodeToString(image)));
		return generateJson(List.of(imagePart, Map.of("text", prompt)), temperature);
	}

	private String generateJson(List<Map<String, Object>> parts, double temperature) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.error("GEMINI_API_KEY 환경변수가 설정되지 않았습니다.");
			throw new BusinessException(ErrorCode.AI_REQUEST_FAILED);
		}

		Map<String, Object> requestBody = Map.of(
			"contents", List.of(Map.of(
				"role", "user",
				"parts", parts)),
			"generationConfig", Map.of(
				"temperature", temperature,
				"responseMimeType", "application/json"));

		try {
			JsonNode response = restClient.post()
				.uri("/v1beta/models/{model}:generateContent", properties.model())
				.header(API_KEY_HEADER, properties.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(JsonNode.class);
			return extractText(response);
		} catch (RestClientResponseException exception) {
			log.warn("Gemini API 요청이 실패했습니다. status={}, body={}",
				exception.getStatusCode().value(), exception.getResponseBodyAsString());
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
				throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED, exception);
			}
			throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, exception);
		} catch (RestClientException exception) {
			log.warn("Gemini API 호출 중 오류가 발생했습니다.", exception);
			throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, exception);
		}
	}

	private String extractText(JsonNode response) {
		if (response == null) {
			return "";
		}
		JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
		return StreamSupport.stream(parts.spliterator(), false)
			.map(part -> part.path("text").asString(""))
			.collect(Collectors.joining("\n"))
			.trim();
	}

}
