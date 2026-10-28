package com.bium.domain.receipt.scan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.receipt.dto.ReceiptScanResponse;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gemini가 영수증에서 읽은 품목 JSON을 화면에 보여 줄 후보로 바꾼다.
 * 형식에 맞지 않는 값은 버리거나 비워서, 사용자가 화면에서 바로 고칠 수 있는 값만 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptItemParser {

	// 재료 일괄 추가 API가 한 번에 받는 수와 같다
	public static final int MAX_ITEMS = 50;
	public static final int MAX_SHELF_LIFE_DAYS = 365;

	private static final int MAX_NAME_LENGTH = 50;
	private static final int MAX_EMOJI_LENGTH = 16;
	// 재료 이름은 레시피 추천 프롬프트에 들어가므로 재료 추가 요청과 같이 줄바꿈·제어 문자를 없앤다
	private static final Pattern NAME_CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+");
	// 이모지는 여러 글자를 잇는 ZWJ(U+200D)가 형식 문자라서 형식 문자는 남긴다
	private static final Pattern EMOJI_CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]+");

	private final ObjectMapper objectMapper;

	public List<ReceiptScanResponse.Item> parse(String text, LocalDate today) {
		JsonNode items = readJson(text).path("items");
		if (!items.isArray()) {
			return List.of();
		}

		List<ReceiptScanResponse.Item> result = new ArrayList<>();
		for (JsonNode item : items) {
			String name = toName(item.path("name"));
			if (name == null) {
				continue;
			}
			Integer shelfLifeDays = toShelfLifeDays(item.path("shelfLifeDays"));
			result.add(new ReceiptScanResponse.Item(name, toEmoji(item.path("emoji")), toStorage(item.path("storage")),
				shelfLifeDays == null ? null : today.plusDays(shelfLifeDays)));
			if (result.size() == MAX_ITEMS) {
				break;
			}
		}
		return result;
	}

	// 영수증 내용이 로그에 남지 않게 응답 본문은 기록하지 않는다
	private JsonNode readJson(String text) {
		String cleaned = text == null ? "" : text.strip();
		int start = cleaned.indexOf('{');
		int end = cleaned.lastIndexOf('}');
		if (start == -1 || end < start) {
			log.warn("영수증 인식 응답에서 JSON을 찾지 못했습니다. length={}", cleaned.length());
			throw new BusinessException(ErrorCode.RECEIPT_SCAN_FAILED);
		}

		try {
			return objectMapper.readTree(cleaned.substring(start, end + 1));
		} catch (JacksonException exception) {
			log.warn("영수증 인식 응답을 JSON으로 해석하지 못했습니다. length={}", cleaned.length());
			throw new BusinessException(ErrorCode.RECEIPT_SCAN_FAILED, exception);
		}
	}

	private String toName(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		String name = NAME_CONTROL_CHARACTER.matcher(node.stringValue()).replaceAll(" ").strip();
		if (name.isEmpty()) {
			return null;
		}
		// 재료 추가 요청의 @Size와 같은 기준(UTF-16 글자 수)으로 자르되, 이모지를 반으로 가르지 않는다
		if (name.length() > MAX_NAME_LENGTH) {
			int end = Character.isHighSurrogate(name.charAt(MAX_NAME_LENGTH - 1)) ? MAX_NAME_LENGTH - 1 : MAX_NAME_LENGTH;
			name = name.substring(0, end).strip();
		}
		return name;
	}

	private String toEmoji(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		String emoji = EMOJI_CONTROL_CHARACTER.matcher(node.stringValue()).replaceAll("").strip();
		return emoji.isEmpty() || emoji.length() > MAX_EMOJI_LENGTH ? null : emoji;
	}

	// 대부분의 장본 식재료가 냉장 보관이라 알 수 없는 값은 냉장실로 둔다
	private StorageType toStorage(JsonNode node) {
		String value = node.isString() ? node.stringValue().strip().toUpperCase(Locale.ROOT) : "";
		return switch (value) {
			case "FREEZER", "냉동", "냉동실" -> StorageType.FREEZER;
			case "ROOM", "실온", "상온" -> StorageType.ROOM;
			default -> StorageType.FRIDGE;
		};
	}

	private Integer toShelfLifeDays(JsonNode node) {
		if (!node.isNumber()) {
			return null;
		}
		long days = Math.round(node.asDouble());
		return days >= 1 && days <= MAX_SHELF_LIFE_DAYS ? (int)days : null;
	}

}
