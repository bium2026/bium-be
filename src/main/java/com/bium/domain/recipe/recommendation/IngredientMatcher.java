package com.bium.domain.recipe.recommendation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재료 이름 비교 규칙이다.
 * 글자와 숫자만 남겨 비교하고, 한쪽 이름이 다른 쪽을 포함하면 같은 재료로 본다.
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
		return value.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
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
		String shorter = name.length() <= otherName.length() ? name : otherName;
		String longer = shorter == name ? otherName : name;
		return shorter.length() >= MIN_PARTIAL_MATCH_LENGTH && longer.contains(shorter);
	}

}
