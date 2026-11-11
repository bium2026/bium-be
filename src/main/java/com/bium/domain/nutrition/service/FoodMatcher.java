package com.bium.domain.nutrition.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.bium.global.nutrition.FoodNutritionClient.Food;

/**
 * 식약처 검색 결과에서 재료 이름에 맞는 식품을 고른다. 검색은 이름 일부만 맞아도 돌려주므로 "두부"로 찾으면 "두부조림"도 온다.
 * 이름이 같은 식품을 먼저 고르고, 없으면 "두부_부침용"·"두부(부침용)"처럼 재료 이름 뒤에 세부 이름만 붙은 식품을 고른다.
 * 다른 음식의 영양 정보를 보여 주지 않도록 그 밖의 식품은 고르지 않는다.
 */
final class FoodMatcher {

	private static final int EXACT = 0;
	private static final int DETAIL = 1;
	private static final int NO_MATCH = -1;
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private FoodMatcher() {
	}

	static Optional<Food> match(String searchName, List<Food> foods) {
		String target = normalize(searchName);
		// 같은 순위에서는 네 값이 다 있는 식품, 원재료성 식품, 이름이 짧은 식품, 식품코드 순으로 고른다
		Comparator<Food> preference = Comparator.<Food>comparingInt(food -> rank(target, food))
			.thenComparing(food -> !food.hasAllValues())
			.thenComparing(food -> !isRawMaterial(food))
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
		if (name.startsWith(target + "_") || name.startsWith(target + "(")) {
			return DETAIL;
		}
		return NO_MATCH;
	}

	// 냉장고 재료는 대부분 조리 전 재료라 같은 이름이면 원재료성 식품을 먼저 고른다
	private static boolean isRawMaterial(Food food) {
		return food.groupName() != null && food.groupName().contains("원재료");
	}

	// 띄어쓰기와 영문 대소문자가 달라도 같은 이름으로 본다
	private static String normalize(String name) {
		return SPACES.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
	}

}
