package com.bium.domain.recipe.recommendation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 추천에 쓰는 냉장고 재료다. FridgeItem에서 추천에 필요한 값만 옮겨 담는다.
 * expiryDate가 null이면 유통기한이 없는 재료로 본다.
 */
public record RecommendationIngredient(
	Long id,
	String name,
	String emoji,
	LocalDate expiryDate
) {

	public boolean isUsable(LocalDate today) {
		return expiryDate == null || !expiryDate.isBefore(today);
	}

	public Long daysLeft(LocalDate today) {
		return expiryDate == null ? null : ChronoUnit.DAYS.between(today, expiryDate);
	}

}
