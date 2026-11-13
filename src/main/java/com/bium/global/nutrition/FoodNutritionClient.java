package com.bium.global.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 공공데이터포털의 식약처 식품영양성분 DB API(FoodNtrCpntDbInfo03)로 식품을 이름으로 찾는다.
 * 이 API는 이름 일부만 맞아도 돌려주고 순서가 정해져 있지 않아서, 재료에 맞는 식품은 부르는 쪽에서 고른다.
 */
@Slf4j
@RequiredArgsConstructor
public class FoodNutritionClient {

	private static final String SUCCESS = "00";
	// 공공데이터포털 API가 결과가 없을 때 주는 코드
	private static final String NO_DATA = "03";
	// 브랜드 제품(상용제품)을 빼고 품목을 대표하는 식품만 찾는다
	private static final String REPRESENTATIVE_ITEM = "품목대표";
	private static final BigDecimal DEFAULT_BASE_AMOUNT = BigDecimal.valueOf(100);
	// nutrition 표의 decimal(7,2)에 들어가는 가장 큰 값. 이보다 크면 잘못 들어간 값으로 보고 버린다
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999.99");
	private static final int MAX_FOOD_CODE_LENGTH = 30;
	private static final int MAX_LOGGED_BODY_LENGTH = 500;
	// "100g", "100 g", "100mL"처럼 오는 기준량에서 숫자만 읽는다
	private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)");

	private final RestClient restClient;
	private final FoodNutritionProperties properties;

	/**
	 * 이름에 검색어가 들어간 식품을 한 페이지(pageNo는 1부터) 돌려준다. 결과가 없으면 빈 페이지를 준다.
	 * 호출이 실패하면(키 없음, 타임아웃, 호출 한도 초과, 서버 오류) NUTRITION_FETCH_FAILED를 던진다.
	 */
	public SearchPage searchByName(String name, int pageNo) {
		// 키가 없는 것은 설정 문제라 요청마다 로그를 남기지 않는다. 서버를 켤 때 FoodNutritionConfig가 한 번 남긴다
		if (!isConfigured()) {
			throw new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED);
		}

		JsonNode response = request(name, pageNo);
		if (response == null) {
			log.warn("식약처 영양성분 API가 빈 응답을 돌려줬습니다.");
			throw new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED);
		}

		String resultCode = response.path("header").path("resultCode").asString("");
		if (NO_DATA.equals(resultCode)) {
			return new SearchPage(List.of(), false);
		}
		if (!SUCCESS.equals(resultCode)) {
			// 키가 등록되지 않았거나 호출 한도를 넘으면 header 대신 OpenAPI_ServiceResponse 형식으로 온다
			log.warn("식약처 영양성분 API가 실패 결과를 돌려줬습니다. resultCode={}, message={}", resultCode,
				errorMessage(response));
			throw new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED);
		}
		JsonNode body = response.path("body");
		long totalCount = body.path("totalCount").asLong(0);
		return new SearchPage(toFoods(body.path("items")), (long)pageNo * properties.pageSize() < totalCount);
	}

	public boolean isConfigured() {
		return properties.serviceKey() != null && !properties.serviceKey().isBlank();
	}

	// 서비스 키에는 +, /, = 같은 글자가 있어서 템플릿 변수로 넘겨 전부 인코딩되게 한다. 그래서 디코딩된 키를 넣어야 한다
	private JsonNode request(String name, int pageNo) {
		try {
			return restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/getFoodNtrCpntDbInq03")
					.queryParam("serviceKey", "{serviceKey}")
					.queryParam("type", "json")
					.queryParam("pageNo", pageNo)
					.queryParam("numOfRows", properties.pageSize())
					.queryParam("DB_CLASS_NM", "{dbClass}")
					.queryParam("FOOD_NM_KR", "{name}")
					.build(Map.of("serviceKey", properties.serviceKey(), "dbClass", REPRESENTATIVE_ITEM, "name", name)))
				.retrieve()
				.body(JsonNode.class);
		} catch (RestClientResponseException exception) {
			// 서버 오류 때 HTML 페이지 전체가 올 수 있어 앞부분만 남긴다
			log.warn("식약처 영양성분 API 요청이 실패했습니다. status={}, body={}",
				exception.getStatusCode().value(), abbreviate(exception.getResponseBodyAsString()));
			throw new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED, exception);
		} catch (RestClientException exception) {
			// 요청 주소에 서비스 키가 들어 있어서, 주소가 메시지에 섞일 수 있는 바깥 예외 대신 가장 안쪽 원인만 남긴다
			Throwable cause = exception.getMostSpecificCause();
			log.warn("식약처 영양성분 API 호출 중 오류가 발생했습니다. error={}: {}", cause.getClass().getSimpleName(),
				cause == exception ? "" : cause.getMessage());
			throw new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED);
		}
	}

	private String abbreviate(String text) {
		return text.length() <= MAX_LOGGED_BODY_LENGTH ? text : text.substring(0, MAX_LOGGED_BODY_LENGTH) + "...";
	}

	private String errorMessage(JsonNode response) {
		String resultMsg = response.path("header").path("resultMsg").asString("");
		if (!resultMsg.isEmpty()) {
			return resultMsg;
		}
		return response.path("OpenAPI_ServiceResponse").path("cmmMsgHeader").path("errMsg").asString("");
	}

	// items는 보통 배열이지만, 결과가 하나면 객체로, XML을 바꾼 형식이면 {"item": [...]}로 올 수 있어 모두 받는다
	private List<Food> toFoods(JsonNode items) {
		if (items.isObject() && items.has("item")) {
			return toFoods(items.path("item"));
		}
		List<JsonNode> nodes = new ArrayList<>();
		if (items.isArray()) {
			items.forEach(nodes::add);
		} else if (items.isObject()) {
			nodes.add(items);
		}
		return nodes.stream()
			.map(this::toFood)
			.filter(Objects::nonNull)
			.toList();
	}

	// 식품코드나 이름이 없으면 저장하거나 화면에 보여 줄 수 없어서 버린다
	private Food toFood(JsonNode item) {
		String foodCode = item.path("FOOD_CD").asString("").strip();
		String foodName = item.path("FOOD_NM_KR").asString("").strip();
		if (foodCode.isEmpty() || foodCode.length() > MAX_FOOD_CODE_LENGTH || foodName.isEmpty()) {
			return null;
		}
		return new Food(
			foodCode,
			foodName,
			blankToNull(item.path("DB_GRP_NM").asString("")),
			toBaseAmount(item.path("SERVING_SIZE").asString("")),
			toAmount(item.path("AMT_NUM1")),
			toAmount(item.path("AMT_NUM6")),
			toAmount(item.path("AMT_NUM3")),
			toAmount(item.path("AMT_NUM4")));
	}

	// 기준량이 mL인 식품(음료)도 1mL를 1g으로 보고 같은 칸에 담는다. 읽지 못하면 100g으로 둔다
	private BigDecimal toBaseAmount(String servingSize) {
		Matcher matcher = LEADING_NUMBER.matcher(servingSize.replace(",", ""));
		if (!matcher.find()) {
			return DEFAULT_BASE_AMOUNT;
		}
		BigDecimal amount = new BigDecimal(matcher.group(1));
		if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
			return DEFAULT_BASE_AMOUNT;
		}
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

	// 값은 "1,010.000"처럼 쉼표가 들어간 문자열로 온다. 비었거나 숫자가 아니거나 범위를 벗어나면 값이 없는 것으로 본다
	private BigDecimal toAmount(JsonNode node) {
		BigDecimal amount;
		if (node.isNumber()) {
			amount = node.decimalValue();
		} else if (node.isString()) {
			String text = node.stringValue().replace(",", "").strip();
			if (text.isEmpty()) {
				return null;
			}
			try {
				amount = new BigDecimal(text);
			} catch (NumberFormatException exception) {
				return null;
			}
		} else {
			return null;
		}
		if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
			return null;
		}
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

	private String blankToNull(String value) {
		return value.isBlank() ? null : value.strip();
	}

	/**
	 * 검색 결과 한 페이지. hasNext는 전체 결과 수(totalCount)로 보아 다음 페이지가 있는지다.
	 */
	public record SearchPage(List<Food> foods, boolean hasNext) {
	}

	/**
	 * 식약처에서 받은 식품 하나. 영양 값은 baseAmountG(보통 100g)당 값이고, 없으면 null이다.
	 */
	public record Food(
		String foodCode,
		String foodName,
		String groupName,
		BigDecimal baseAmountG,
		BigDecimal caloriesKcal,
		BigDecimal carbohydrateG,
		BigDecimal proteinG,
		BigDecimal fatG
	) {

		public boolean hasAnyValue() {
			return caloriesKcal != null || carbohydrateG != null || proteinG != null || fatG != null;
		}

		public boolean hasAllValues() {
			return caloriesKcal != null && carbohydrateG != null && proteinG != null && fatG != null;
		}

	}

}
