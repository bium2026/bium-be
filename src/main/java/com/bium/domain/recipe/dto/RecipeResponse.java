package com.bium.domain.recipe.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeDifficulty;
import com.bium.domain.recipe.entity.RecipeIngredient;

/**
 * fridgeMatches는 재료 중 냉장고에 있는 재료의 이름이다.
 */
public record RecipeResponse(
	Long id,
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
	Nutrition nutrition,
	List<String> fridgeMatches,
	LocalDateTime createdAt
) {

	private static final int MAX_FRIDGE_MATCHES = 6;

	public static RecipeResponse of(Recipe recipe, List<RecipeIngredient> recipeIngredients) {
		List<Ingredient> ingredients = recipeIngredients.stream()
			.map(ingredient -> new Ingredient(ingredient.getName(), ingredient.getAmount(), ingredient.getSource(),
				ingredient.isRequired()))
			.toList();
		return new RecipeResponse(
			recipe.getId(),
			recipe.getTitle(),
			recipe.getEmoji(),
			recipe.getSummary(),
			recipe.getCookMinutes(),
			recipe.getDifficulty(),
			recipe.getServings(),
			recipe.getCaloriesKcal(),
			ingredients,
			recipe.getSteps() == null ? List.of() : recipe.getSteps(),
			recipe.getTips() == null ? List.of() : recipe.getTips(),
			new Nutrition(recipe.getProteinG(), recipe.getCarbohydrateG(), recipe.getFatG()),
			ingredients.stream()
				.filter(ingredient -> ingredient.source() == IngredientSource.FRIDGE)
				.map(Ingredient::name)
				.distinct()
				.limit(MAX_FRIDGE_MATCHES)
				.toList(),
			recipe.getCreatedAt());
	}

	public record Ingredient(String name, String amount, IngredientSource source, boolean required) {
	}

	// 값은 g 단위이며 AI가 주지 않으면 null이다
	public record Nutrition(Integer proteinG, Integer carbohydrateG, Integer fatG) {
	}

}
