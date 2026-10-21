package com.bium.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;
import com.bium.domain.recipe.recommendation.RecipeCandidateParser;
import com.bium.domain.recipe.recommendation.RecipeCandidateSelector;
import com.bium.domain.recipe.recommendation.RecipePromptBuilder;
import com.bium.domain.recipe.recommendation.RecommendationIngredient;
import com.bium.domain.recipe.recommendation.RecommendedRecipe;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.gemini.GeminiClient;

import tools.jackson.databind.json.JsonMapper;

class RecipeRecommendationServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
	private static final String GEMINI_RESPONSE = """
		{"recipes": [
		  {"name": "두부 계란 볶음밥", "cookTimeMinutes": 12, "difficulty": "EASY", "servings": 1,
		   "caloriesKcal": 430, "emoji": "🍳", "summary": "두부와 계란으로 만드는 볶음밥",
		   "ingredients": [
		     {"name": "두부", "amount": "1/2모", "required": true},
		     {"name": "계란", "amount": "2개", "required": true},
		     {"name": "간장", "amount": "1큰술", "required": true}],
		   "steps": ["두부를 굽는다", "계란과 밥을 볶는다"], "tips": ["찬밥을 쓴다"],
		   "nutrition": {"proteinG": 24, "carbohydrateG": 52, "fatG": 14},
		   "fridgeMatches": ["두부", "계란"]},
		  {"name": "소고기 무국", "steps": ["끓인다"],
		   "ingredients": [{"name": "소고기", "amount": "100g", "required": true}],
		   "fridgeMatches": []}
		]}
		""";

	private final GeminiClient geminiClient = mock(GeminiClient.class);
	private final RecipeRecommendationService service = new RecipeRecommendationService(
		geminiClient,
		new RecipePromptBuilder(),
		new RecipeCandidateParser(JsonMapper.shared()),
		new RecipeCandidateSelector(),
		Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), SEOUL));

	private final RecommendationIngredient tofu = new RecommendationIngredient(1L, "두부", "🧊", TODAY.plusDays(2));
	private final RecommendationIngredient egg = new RecommendationIngredient(2L, "계란", "🥚", TODAY);
	private final RecommendationIngredient expiredMilk =
		new RecommendationIngredient(3L, "우유", "🥛", TODAY.minusDays(1));
	private final RecommendationIngredient rice = new RecommendationIngredient(4L, "쌀", "🍚", null);

	@Test
	void 걸러낸_추천_레시피와_사용한_냉장고_재료를_돌려준다() {
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);

		List<RecommendedRecipe> recipes = service.recommend(List.of(tofu, egg, expiredMilk), true);

		assertThat(recipes).hasSize(1);
		RecommendedRecipe recipe = recipes.get(0);
		assertThat(recipe.name()).isEqualTo("두부 계란 볶음밥");
		assertThat(recipe.difficulty()).isEqualTo(RecipeDifficulty.EASY);
		assertThat(recipe.ingredients()).extracting(RecommendedRecipe.Ingredient::source)
			.containsExactly(IngredientSource.FRIDGE, IngredientSource.FRIDGE, IngredientSource.BASIC);
		assertThat(recipe.nutrition()).isEqualTo(new RecommendedRecipe.Nutrition(24, 52, 14));
	}

	@Test
	void 프롬프트에는_유통기한이_남은_재료만_가까운_순서로_넣는다() {
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);

		service.recommend(List.of(rice, tofu, expiredMilk, egg), false);

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(geminiClient).generateJson(prompt.capture());
		assertThat(prompt.getValue())
			.contains("- 계란 (🥚) / D-0\n- 두부 (🧊) / D-2\n- 쌀 (🍚) / 유통기한 없음")
			.contains("임박 재료 우선 조건은 약하게 반영해.")
			.doesNotContain("우유");
	}

	@Test
	void 쓸_수_있는_재료가_없으면_AI를_호출하지_않는다() {
		assertThatThrownBy(() -> service.recommend(List.of(expiredMilk), true))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NO_USABLE_INGREDIENT);
		verify(geminiClient, never()).generateJson(anyString());
	}

	@Test
	void 걸러낸_뒤_레시피가_없으면_결과_없음으로_처리한다() {
		given(geminiClient.generateJson(anyString())).willReturn("{\"recipes\": []}");

		assertThatThrownBy(() -> service.recommend(List.of(tofu), true))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.RECIPE_NO_RESULT);
	}

}
