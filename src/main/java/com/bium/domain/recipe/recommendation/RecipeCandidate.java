package com.bium.domain.recipe.recommendation;

import java.util.List;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;

/**
 * AI가 준 레시피 후보다. 값이 없거나 형식이 틀리면 null이고, 걸러내기 전 상태다.
 */
public record RecipeCandidate(
	String name,
	String emoji,
	String summary,
	Integer cookTimeMinutes,
	RecipeDifficulty difficulty,
	Integer servings,
	Integer caloriesKcal,
	List<Ingredient> ingredients,
	List<String> steps,
	List<String> tips,
	Integer proteinG,
	Integer carbohydrateG,
	Integer fatG,
	List<String> fridgeMatches
) {

	public record Ingredient(String name, String amount, boolean required) {
	}

}
