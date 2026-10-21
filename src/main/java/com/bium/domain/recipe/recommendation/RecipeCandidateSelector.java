package com.bium.domain.recipe.recommendation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;

/**
 * AI 후보 중 냉장고 재료로 만들 수 있는 평범한 집밥만 남기고, 서로 다른 레시피 4개를 고른다.
 */
@Component
public class RecipeCandidateSelector {

	public static final int RECOMMENDATION_COUNT = 4;

	private static final List<String> ODD_RECIPE_TERMS = List.of(
		"스무디", "주스", "음료", "칵테일", "아이스크림", "케이크", "쿠키", "잼", "퓨전", "실험", "분자", "디저트");
	private static final String DEFAULT_TIP = "가지고 있는 재료를 먼저 사용하면 음식물 쓰레기를 줄일 수 있어요.";
	private static final int MAX_FRIDGE_MATCHES = 6;
	private static final int MAX_STEPS = 6;
	private static final int MAX_TIPS = 3;

	/**
	 * availableIngredients는 유통기한이 남은 재료다.
	 */
	public List<RecommendedRecipe> select(List<RecipeCandidate> candidates,
		List<RecommendationIngredient> availableIngredients) {
		IngredientMatcher matcher = new IngredientMatcher(
			availableIngredients.stream().map(RecommendationIngredient::name).toList());

		List<RecipeCandidate> allowedCandidates = candidates.stream()
			.filter(candidate -> !hasUnavailableRequiredIngredient(candidate, matcher))
			.toList();

		List<RecommendedRecipe> recipes = new ArrayList<>();
		for (int index = 0; index < allowedCandidates.size(); index++) {
			RecommendedRecipe recipe = normalize(allowedCandidates.get(index), index, matcher);
			if (!recipe.steps().isEmpty() && isPracticalRecipe(recipe, matcher)) {
				recipes.add(recipe);
			}
		}
		return selectDiverseRecipes(recipes);
	}

	private boolean hasUnavailableRequiredIngredient(RecipeCandidate candidate, IngredientMatcher matcher) {
		return candidate.ingredients().stream()
			.anyMatch(ingredient -> ingredient.required() && !matcher.isAllowed(ingredient.name()));
	}

	private RecommendedRecipe normalize(RecipeCandidate candidate, int index, IngredientMatcher matcher) {
		List<RecommendedRecipe.Ingredient> ingredients = candidate.ingredients().stream()
			.filter(ingredient -> matcher.isAllowed(ingredient.name()))
			.map(ingredient -> new RecommendedRecipe.Ingredient(
				limit(ingredient.name(), 50),
				textOrDefault(ingredient.amount(), "적당량", 50),
				matcher.isAvailable(ingredient.name()) ? IngredientSource.FRIDGE : IngredientSource.BASIC,
				ingredient.required()))
			.toList();

		List<String> fridgeMatches = candidate.fridgeMatches().stream()
			.filter(matcher::isAvailable)
			.limit(MAX_FRIDGE_MATCHES)
			.toList();
		if (fridgeMatches.isEmpty()) {
			fridgeMatches = ingredients.stream()
				.map(RecommendedRecipe.Ingredient::name)
				.filter(matcher::isAvailable)
				.limit(MAX_FRIDGE_MATCHES)
				.toList();
		}

		List<String> tips = candidate.tips().stream().limit(MAX_TIPS).map(tip -> limit(tip, 500)).toList();

		return new RecommendedRecipe(
			textOrDefault(candidate.name(), "AI 추천 레시피 " + (index + 1), 100),
			textOrDefault(candidate.emoji(), "🍽️", 16),
			textOrDefault(candidate.summary(), "냉장고 재료를 활용한 맞춤형 레시피입니다.", 255),
			positiveOrDefault(candidate.cookTimeMinutes(), 15),
			candidate.difficulty() == null ? RecipeDifficulty.EASY : candidate.difficulty(),
			positiveOrDefault(candidate.servings(), 1),
			candidate.caloriesKcal(),
			ingredients,
			candidate.steps().stream().limit(MAX_STEPS).map(step -> limit(step, 500)).toList(),
			tips.isEmpty() ? List.of(DEFAULT_TIP) : tips,
			new RecommendedRecipe.Nutrition(candidate.proteinG(), candidate.carbohydrateG(), candidate.fatG()),
			fridgeMatches);
	}


	private boolean isPracticalRecipe(RecommendedRecipe recipe, IngredientMatcher matcher) {
		String recipeText = String.join(" ", Stream.of(
				Stream.of(recipe.name(), recipe.summary()),
				recipe.ingredients().stream().map(RecommendedRecipe.Ingredient::name),
				recipe.fridgeMatches().stream())
			.flatMap(stream -> stream)
			.toList());

		boolean hasOddTerm = ODD_RECIPE_TERMS.stream().anyMatch(recipeText::contains);
		boolean usesFridgeIngredient = matcher.availableNames().stream().anyMatch(recipeText::contains);
		boolean hasUsableName = IngredientMatcher.normalize(recipe.name()).length() >= 2;
		boolean hasIngredients = !recipe.ingredients().isEmpty() && !recipe.fridgeMatches().isEmpty();

		return !hasOddTerm && usesFridgeIngredient && hasUsableName && hasIngredients;
	}

	// 비슷한 레시피를 빼고 고른 뒤, 개수가 모자라면 남은 후보로 채운다
	private List<RecommendedRecipe> selectDiverseRecipes(List<RecommendedRecipe> candidates) {
		List<RecommendedRecipe> selected = new ArrayList<>();
		Set<Integer> selectedIndexes = new HashSet<>();
		for (int index = 0; index < candidates.size() && selected.size() < RECOMMENDATION_COUNT; index++) {
			RecommendedRecipe candidate = candidates.get(index);
			if (selected.stream().noneMatch(recipe -> isSimilarRecipe(candidate, recipe))) {
				selected.add(candidate);
				selectedIndexes.add(index);
			}
		}
		for (int index = 0; index < candidates.size() && selected.size() < RECOMMENDATION_COUNT; index++) {
			if (!selectedIndexes.contains(index)) {
				selected.add(candidates.get(index));
			}
		}
		return selected;
	}

	private boolean isSimilarRecipe(RecommendedRecipe recipe, RecommendedRecipe selectedRecipe) {
		if (IngredientMatcher.matches(recipe.name(), selectedRecipe.name())) {
			return true;
		}

		Set<String> recipeIngredients = ingredientNames(recipe);
		Set<String> selectedIngredients = ingredientNames(selectedRecipe);
		int smallerIngredientCount = Math.min(recipeIngredients.size(), selectedIngredients.size());
		if (smallerIngredientCount < 2) {
			return false;
		}

		Set<String> sharedIngredients = new HashSet<>(recipeIngredients);
		sharedIngredients.retainAll(selectedIngredients);
		return sharedIngredients.size() >= smallerIngredientCount;
	}

	private Set<String> ingredientNames(RecommendedRecipe recipe) {
		Set<String> names = new LinkedHashSet<>();
		Stream.concat(recipe.fridgeMatches().stream(),
				recipe.ingredients().stream().map(RecommendedRecipe.Ingredient::name))
			.map(IngredientMatcher::normalize)
			.filter(name -> !name.isEmpty())
			.forEach(names::add);
		return names;
	}

	private String textOrDefault(String value, String defaultValue, int maxLength) {
		return limit(value == null || value.isBlank() ? defaultValue : value, maxLength);
	}

	// 이모지가 중간에 잘리지 않게 코드 포인트 기준으로 자른다
	private String limit(String value, int maxLength) {
		if (value.codePointCount(0, value.length()) <= maxLength) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxLength));
	}

	private int positiveOrDefault(Integer value, int defaultValue) {
		return value == null || value <= 0 ? defaultValue : value;
	}

}
