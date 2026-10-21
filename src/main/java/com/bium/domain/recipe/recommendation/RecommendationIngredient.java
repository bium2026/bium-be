package com.bium.domain.recipe.recommendation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 추천에 쓰는 냉장고 재료다. 냉장고 엔티티가 생기면 그 값을 이 형태로 바꿔 넘긴다.
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
