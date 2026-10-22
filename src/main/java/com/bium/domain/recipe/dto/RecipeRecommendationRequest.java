package com.bium.domain.recipe.dto;

/**
 * 본문을 보내지 않아도 된다. prioritizeUrgent가 없으면 임박 재료 우선 조건을 약하게 반영한다.
 */
public record RecipeRecommendationRequest(Boolean prioritizeUrgent) {

	public boolean isPrioritizeUrgent() {
		return Boolean.TRUE.equals(prioritizeUrgent);
	}

}
