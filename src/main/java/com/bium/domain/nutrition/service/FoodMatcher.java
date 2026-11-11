package com.bium.domain.nutrition.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.bium.global.nutrition.FoodNutritionClient.Food;

/**
 * 식약처 검색 결과에서 재료 이름에 맞는 식품을 고른다. 검색은 이름 일부만 맞아도 돌려주므로 "두부"로 찾으면 "두부조림"도 온다.
 * 식약처 이름은 음식이면 "두부_부침용", 원재료면 "양파, 생것"·"파, 대파, 생것"처럼 쉼표로 이어 쓴다. 그래서 아래 순서로 고르고,
 * 다른 음식의 영양 정보를 보여 주지 않도록 그 밖의 식품은 고르지 않는다.
 * <ol>
 *   <li>이름이 같은 식품 ("두부")</li>
 *   <li>재료 이름 뒤에 세부 이름만 붙은 식품 ("양파, 생것", "두부_부침용", "두부(부침용)")</li>
 *   <li>쉼표 뒤 세부 이름이 재료 이름이고 앞 이름(종류)을 품은 식품 ("파, 대파, 생것", "김치, 배추 김치")</li>
 * </ol>
 */
final class FoodMatcher {

	private static final int EXACT = 0;
	private static final int DETAIL = 1;
	private static final int PART = 2;
	private static final int NO_MATCH = -1;
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final String RAW_STATE = "생것";

	private FoodMatcher() {
	}

	static Optional<Food> match(String searchName, List<Food> foods) {
		String target = normalize(searchName);
		// 같은 순위에서는 네 값이 다 있는 식품, 원재료성 식품, 익히지 않은(생것) 식품, 이름이 짧은 식품, 식품코드 순으로 고른다.
		// 도넛을 그리려면 네 값이 모두 있어야 해서 이를 가장 먼저 본다
		Comparator<Food> preference = Comparator.<Food>comparingInt(food -> rank(target, food))
			.thenComparing(food -> !food.hasAllValues())
			.thenComparing(food -> !isRawMaterial(food))
			.thenComparing(food -> !normalize(food.foodName()).contains(RAW_STATE))
			.thenComparingInt(food -> normalize(food.foodName()).length())
			.thenComparing(Food::foodCode);
		return foods.stream()
			.filter(Food::hasAnyValue)
			.filter(food -> rank(target, food) != NO_MATCH)
			.min(preference);
	}

	// 이름이 같은 식품이 있으면 다음 페이지를 더 받아 보지 않는다
	static boolean hasExactMatch(String searchName, List<Food> foods) {
		String target = normalize(searchName);
		return foods.stream()
			.filter(Food::hasAnyValue)
			.anyMatch(food -> rank(target, food) == EXACT);
	}

	private static int rank(String target, Food food) {
		String name = normalize(food.foodName());
		if (name.equals(target)) {
			return EXACT;
		}
		if (name.startsWith(target + ",") || name.startsWith(target + "_") || name.startsWith(target + "(")) {
			return DETAIL;
		}
		if (isSubtypeOfFirstPart(target, name.split(","))) {
			return PART;
		}
		return NO_MATCH;
	}

	// "파, 대파"의 대파처럼 앞 이름(종류)을 품은 세부 이름만 고른다. "엿, 쌀"의 쌀은 엿의 재료라서 쌀로 고르지 않는다
	private static boolean isSubtypeOfFirstPart(String target, String[] parts) {
		return parts.length > 1
			&& target.contains(parts[0])
			&& Arrays.asList(parts).subList(1, parts.length).contains(target);
	}

	// 냉장고 재료는 대부분 조리 전 재료라 같은 순위면 원재료성 식품을 먼저 고른다
	private static boolean isRawMaterial(Food food) {
		return food.groupName() != null && food.groupName().contains("원재료");
	}

	// 띄어쓰기와 영문 대소문자가 달라도 같은 이름으로 본다
	private static String normalize(String name) {
		return SPACES.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
	}

}
