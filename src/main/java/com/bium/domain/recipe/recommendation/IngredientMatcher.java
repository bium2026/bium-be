package com.bium.domain.recipe.recommendation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재료 이름 비교 규칙이다.
 * 글자와 숫자만 남겨 비교하고, 한쪽 이름이 다른 쪽을 포함하면 같은 재료로 본다.
 * 다만 "배추"와 "양배추"처럼 이름은 겹치지만 다른 재료인 쌍은 DISTINCT_FORMS에 적어 두고 같은 재료로 보지 않는다.
 * "계란"은 "달걀"로 바꿔서 비교하므로 두 이름은 같은 재료다.
 * "파"와 "양파"처럼 한 글자 이름은 다른 재료에 쉽게 포함되므로, 한 글자 이름은 완전히 같을 때만 같은 재료로 본다.
 */
public class IngredientMatcher {

	// 냉장고에 없어도 쓸 수 있는 기본 재료와 양념
	private static final Set<String> BASIC_INGREDIENTS = List.of(
			"밥", "물", "소금", "후추", "설탕", "식용유", "참기름", "간장",
			"고추장", "된장", "다진 마늘", "마늘", "깨", "식초", "버터", "밀가루")
		.stream()
		.map(IngredientMatcher::normalize)
		.collect(Collectors.toUnmodifiableSet());

	// 한 이름이 다른 이름에 들어 있지만 서로 다른 재료인 쌍: {기본 재료, 기본 재료를 포함한 다른 재료}
	private static final List<String[]> DISTINCT_FORMS = List.of(
		new String[] {"배추", "양배추"},
		new String[] {"배추", "배추김치"},
		new String[] {"고추", "고추장"},
		new String[] {"버터", "땅콩버터"},
		new String[] {"새우", "새우젓"},
		new String[] {"두부", "순두부"},
		new String[] {"오이", "오이지"});

	// 같은 재료의 다른 표기: 앞 이름을 뒤 이름으로 바꿔서 비교한다 (영양 검색의 계란 -> 달걀과 같다)
	private static final String ALIAS_FROM = "계란";
	private static final String ALIAS_TO = "달걀";

	// 부분 일치로 비교할 이름의 최소 글자 수
	private static final int MIN_PARTIAL_MATCH_LENGTH = 2;

	private final List<String> availableNames;
	private final List<String> normalizedAvailableNames;

	public IngredientMatcher(List<String> availableNames) {
		this.availableNames = List.copyOf(availableNames);
		this.normalizedAvailableNames = availableNames.stream()
			.map(IngredientMatcher::normalize)
			.filter(name -> !name.isEmpty())
			.toList();
	}

	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("[^\\p{L}\\p{N}]", "")
			.toLowerCase(Locale.ROOT)
			.replace(ALIAS_FROM, ALIAS_TO);
	}

	public static boolean matches(String name, String otherName) {
		String normalizedName = normalize(name);
		String normalizedOtherName = normalize(otherName);
		if (normalizedName.isEmpty() || normalizedOtherName.isEmpty()) {
			return false;
		}
		return overlaps(normalizedName, normalizedOtherName);
	}

	public List<String> availableNames() {
		return availableNames;
	}

	public boolean isAvailable(String name) {
		String normalizedName = normalize(name);
		if (normalizedName.isEmpty()) {
			return false;
		}
		return normalizedAvailableNames.stream()
			.anyMatch(available -> overlaps(normalizedName, available));
	}

	public boolean isBasic(String name) {
		String normalizedName = normalize(name);
		return !normalizedName.isEmpty() && BASIC_INGREDIENTS.contains(normalizedName);
	}

	public boolean isAllowed(String name) {
		return isAvailable(name) || isBasic(name);
	}

	private static boolean overlaps(String name, String otherName) {
		if (name.equals(otherName)) {
			return true;
		}
		for (String[] form : DISTINCT_FORMS) {
			if (isDistinctForm(name, otherName, form[0], form[1])
				|| isDistinctForm(otherName, name, form[0], form[1])) {
				return false;
			}
		}
		String shorter = name.length() <= otherName.length() ? name : otherName;
		String longer = shorter == name ? otherName : name;
		return shorter.length() >= MIN_PARTIAL_MATCH_LENGTH && longer.contains(shorter);
	}

	// name은 다른 재료(form)를 담고 있고, otherName은 기본 재료(base)만 담고 있으면 true
	private static boolean isDistinctForm(String name, String otherName, String base, String form) {
		return name.contains(form) && !otherName.contains(form) && otherName.contains(base);
	}

}
