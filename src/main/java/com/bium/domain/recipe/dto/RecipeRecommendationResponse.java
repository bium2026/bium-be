package com.bium.domain.recipe.dto;

import java.util.List;

import com.bium.domain.recipe.entity.RecipeDifficulty;
import com.bium.domain.recipe.recommendation.RecommendationIngredient;
import com.bium.domain.recipe.recommendation.RecommendedRecipe;

/**
 * usedIngredients는 요청한 재료 중 레시피에 쓰인 재료의 이름이다.
 */
public record RecipeRecommendationResponse(List<Recipe> recipes) {

	public static RecipeRecommendationResponse from(List<RecommendedRecipe> recipes) {
		return new RecipeRecommendationResponse(recipes.stream().map(Recipe::from).toList());
	}

	public record Recipe(
		String name,
		String emoji,
		String summary,
		int cookTimeMinutes,
		RecipeDifficulty difficulty,
		int servings,
		Integer caloriesKcal,
		List<RecommendedRecipe.Ingredient> ingredients,
		List<String> steps,
		List<String> tips,
		RecommendedRecipe.Nutrition nutrition,
		List<String> fridgeMatches,
		List<String> usedIngredients
	) {

		static Recipe from(RecommendedRecipe recipe) {
			return new Recipe(
				recipe.name(),
				recipe.emoji(),
				recipe.summary(),
				recipe.cookTimeMinutes(),
				recipe.difficulty(),
				recipe.servings(),
				recipe.caloriesKcal(),
				recipe.ingredients(),
				recipe.steps(),
				recipe.tips(),
				recipe.nutrition(),
				recipe.fridgeMatches(),
				recipe.usedIngredients().stream().map(RecommendationIngredient::name).toList());
		}

	}

}
