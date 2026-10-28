package com.bium.domain.recipe.dto;

import java.util.List;

import com.bium.domain.ingredient.dto.FridgeItemResponse;

/**
 * 요리 완료 전에 레시피 재료마다 뺄 냉장고 재료 후보를 보여 준다.
 * candidates는 유통기한이 빠른 순이며 첫 번째가 서버가 추천하는 재료다. 맞는 재료가 없으면 빈 배열이다.
 */
public record CookingCandidatesResponse(Long recipeId, List<IngredientCandidates> ingredients) {

	public record IngredientCandidates(String name, String amount, List<FridgeItemResponse> candidates) {
	}

}
