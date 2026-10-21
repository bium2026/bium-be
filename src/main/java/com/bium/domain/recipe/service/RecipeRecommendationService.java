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
 * 냉장고 재료로 Gemini에 레시피를 요청하고, 걸러낸 추천 레시피를 돌려준다.
 * 저장은 레시피 엔티티가 생긴 뒤 붙인다.
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
		List<RecommendationIngredient> usableIngredients = ingredients.stream()
			.filter(ingredient -> ingredient.isUsable(today))
			.sorted(BY_EXPIRY_DATE)
			.toList();
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

}
