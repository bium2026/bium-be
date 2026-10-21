package com.bium.domain.recipe.recommendation;

import java.util.List;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;

/**
 * 걸러내고 기본값을 채운 추천 레시피다.
 * fridgeMatches는 AI가 준 이름 중 냉장고에 실제로 있는 것만, usedIngredients는 그 이름과 연결한 냉장고 재료다.
 */
public record RecommendedRecipe(
	String name,
	String emoji,
	String summary,
	int cookTimeMinutes,
	RecipeDifficulty difficulty,
	int servings,
	Integer caloriesKcal,
	List<Ingredient> ingredients,
	List<String> steps,
	List<String> tips,
	Nutrition nutrition,
	List<String> fridgeMatches,
	List<RecommendationIngredient> usedIngredients
) {

	public record Ingredient(String name, String amount, IngredientSource source, boolean required) {
	}

	// 값은 g 단위이며 AI가 주지 않으면 null이다
	public record Nutrition(Integer proteinG, Integer carbohydrateG, Integer fatG) {
	}

}
