package com.bium.global.kamis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * KAMIS 일별 부류별 소매가격을 받는다.
 * 시세는 화면 보조 정보라서 실패해도 예외를 던지지 않고 로그를 남긴 뒤 빈 목록을 돌려준다.
 */
@Slf4j
@RequiredArgsConstructor
public class KamisClient {

	private static final String RETAIL = "01";
	private static final String SUCCESS = "000";
	private static final int MAX_NAME_LENGTH = 50;
	private static final int MAX_UNIT_LENGTH = 20;
	private static final long MAX_PRICE = 100_000_000L;

	// KAMIS는 JSON을 text/plain으로 보낸다. 문자열로 받으면 charset이 빠졌을 때 한글이 깨져서 바이트로 받아 UTF-8 JSON으로 읽는다
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final RestClient restClient;
	private final KamisProperties properties;

	public List<KamisPrice> fetchRetailPrices(String categoryCode, LocalDate regDay) {
		if (isBlank(properties.certKey()) || isBlank(properties.certId())) {
			log.error("KAMIS_CERT_KEY 또는 KAMIS_CERT_ID 환경변수가 설정되지 않았습니다.");
			return List.of();
		}

		byte[] body;
		try {
			body = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/service/price/xml.do")
					.queryParam("action", "dailyPriceByCategoryList")
					.queryParam("p_product_cls_code", RETAIL)
					.queryParam("p_item_category_code", categoryCode)
					.queryParam("p_regday", regDay.toString())
					.queryParam("p_convert_kg_yn", "N")
					.queryParam("p_returntype", "json")
					.queryParam("p_cert_key", properties.certKey())
					.queryParam("p_cert_id", properties.certId())
					.build())
				.retrieve()
				.body(byte[].class);
		} catch (RestClientException exception) {
			log.warn("KAMIS API 호출 중 오류가 발생했습니다. category={}", categoryCode, exception);
			return List.of();
		}
		return parse(categoryCode, body);
	}

	private List<KamisPrice> parse(String categoryCode, byte[] body) {
		JsonNode data;
		try {
			data = JSON_MAPPER.readTree(body == null ? new byte[0] : body).path("data");
		} catch (JacksonException exception) {
			log.warn("KAMIS 응답을 읽지 못했습니다. category={}", categoryCode, exception);
			return List.of();
		}

		// 실패하면 data가 ["200"]처럼 결과 코드만 담은 배열로 온다
		if (!data.isObject()) {
			log.warn("KAMIS 응답에 가격이 없습니다. category={}, data={}", categoryCode, data);
			return List.of();
		}
		String errorCode = data.path("error_code").asString("");
		if (!SUCCESS.equals(errorCode)) {
			log.warn("KAMIS 응답이 실패했습니다. category={}, code={}", categoryCode, errorCode);
			return List.of();
		}

		JsonNode items = data.path("item");
		List<JsonNode> rows = new ArrayList<>();
		if (items.isArray()) {
			items.forEach(rows::add);
		} else if (items.isObject()) {
			rows.add(items);
		}

		List<KamisPrice> prices = new ArrayList<>();
		for (JsonNode row : rows) {
			String itemName = row.path("item_name").asString("").trim();
			String kindName = row.path("kind_name").asString("").trim();
			String unit = row.path("unit").asString("").trim();
			if (itemName.isEmpty() || itemName.length() > MAX_NAME_LENGTH || kindName.length() > MAX_NAME_LENGTH
				|| unit.length() > MAX_UNIT_LENGTH) {
				continue;
			}
			prices.add(new KamisPrice(itemName, kindName, row.path("rank").asString("").trim(), unit,
				parsePrice(row.path("dpr1").asString("")), parsePrice(row.path("dpr3").asString(""))));
		}
		return prices;
	}

	// "3,450"처럼 쉼표가 들어 있고, 조사하지 않은 날은 "-" 같은 숫자가 아닌 값이 온다
	static Integer parsePrice(String raw) {
		String digits = raw.replace(",", "").trim();
		if (digits.isEmpty() || digits.length() > 9 || !digits.chars().allMatch(Character::isDigit)) {
			return null;
		}
		long price = Long.parseLong(digits);
		return price > 0 && price < MAX_PRICE ? (int) price : null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
