package com.bium.domain.recipe.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bium.domain.recipe.recommendation.RecipeCandidate;
import com.bium.domain.recipe.recommendation.RecipeCandidateParser;
import com.bium.domain.recipe.recommendation.RecipeCandidateSelector;
import com.bium.domain.recipe.recommendation.RecipePromptBuilder;
import com.bium.domain.recipe.recommendation.RecommendationIngredient;
import com.bium.domain.recipe.recommendation.RecommendedRecipe;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.gemini.GeminiClient;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고 재료나 만들고 싶은 음식으로 Gemini에 레시피를 요청하고, 걸러낸 레시피를 돌려준다.
 * 저장은 하지 않으며, 냉장고 조회와 저장은 RecipeService가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class RecipeRecommendationService {

	private static final Comparator<RecommendationIngredient> BY_EXPIRY_DATE = Comparator.comparing(
		RecommendationIngredient::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()));

	private final GeminiClient geminiClient;
	private final RecipePromptBuilder promptBuilder;
	private final RecipeCandidateParser candidateParser;
	private final RecipeCandidateSelector candidateSelector;
	private final Clock clock;

	public List<RecommendedRecipe> recommend(List<RecommendationIngredient> ingredients, boolean prioritizeUrgent) {
		LocalDate today = LocalDate.now(clock);
		List<RecommendationIngredient> usableIngredients = usableIngredients(ingredients, today);
		if (usableIngredients.isEmpty()) {
			throw new BusinessException(ErrorCode.NO_USABLE_INGREDIENT);
		}

		String prompt = promptBuilder.build(usableIngredients, today, prioritizeUrgent);
		List<RecipeCandidate> candidates = candidateParser.parse(geminiClient.generateJson(prompt));
		List<RecommendedRecipe> recipes = candidateSelector.select(candidates, usableIngredients);
		if (recipes.isEmpty()) {
			throw new BusinessException(ErrorCode.RECIPE_NO_RESULT);
		}
		return recipes;
	}

	// 유통기한이 지난 재료는 없는 재료로 보고 사야 할 재료로 알려 준다
	public RecommendedRecipe recommendTarget(String dishName, List<RecommendationIngredient> ingredients) {
		LocalDate today = LocalDate.now(clock);
		List<RecommendationIngredient> usableIngredients = usableIngredients(ingredients, today);

		String prompt = promptBuilder.buildTarget(dishName, usableIngredients, today);
		List<RecipeCandidate> candidates = candidateParser.parse(geminiClient.generateJson(prompt));
		// 음식 이름이 아니면 빈 목록을 달라고 했으므로, 후보가 없으면 다시 시도해도 같은 결과로 본다
		if (candidates.isEmpty()) {
			throw new BusinessException(ErrorCode.DISH_NOT_RECOGNIZED);
		}
		return candidateSelector.selectTarget(candidates, usableIngredients, dishName)
			.orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NO_RESULT));
	}

	private List<RecommendationIngredient> usableIngredients(List<RecommendationIngredient> ingredients,
		LocalDate today) {
		return ingredients.stream()
			.filter(ingredient -> ingredient.isUsable(today))
			.sorted(BY_EXPIRY_DATE)
			.toList();
	}

}
