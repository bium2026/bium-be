package com.bium.domain.recipe.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.recipe.dto.RecipeRecommendationResponse;
import com.bium.domain.recipe.dto.RecipeResponse;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.recipe.entity.RecipeType;
import com.bium.domain.recipe.recommendation.RecommendationIngredient;
import com.bium.domain.recipe.recommendation.RecommendationRateLimiter;
import com.bium.domain.recipe.recommendation.RecommendedRecipe;
import com.bium.domain.recipe.repository.RecipeIngredientRepository;
import com.bium.domain.recipe.repository.RecipeRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecipeService {

	private final RecipeRecommendationService recommendationService;
	private final RecommendationRateLimiter rateLimiter;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final FridgeRepository fridgeRepository;
	private final FridgeItemRepository fridgeItemRepository;
	private final UserRepository userRepository;
	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	/**
	 * 냉장고 재료로 레시피를 추천받아 저장한다.
	 * Gemini 응답을 기다리는 동안 DB 연결을 잡고 있지 않도록 저장할 때만 트랜잭션을 연다.
	 */
	public RecipeRecommendationResponse recommend(Long userId, Long fridgeId, boolean prioritizeUrgent) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		List<RecommendationIngredient> ingredients = fridgeItemRepository.findAllByFridgeIdAndDeletedAtIsNull(fridgeId)
			.stream()
			.map(item -> new RecommendationIngredient(item.getId(), item.getName(), item.getEmoji(),
				item.getExpiresAt()))
			.toList();
		// 쓸 재료가 없는 요청은 Gemini를 부르지 않으므로 호출 수를 쓰기 전에 거절한다
		LocalDate today = LocalDate.now(clock);
		if (ingredients.stream().noneMatch(ingredient -> ingredient.isUsable(today))) {
			throw new BusinessException(ErrorCode.NO_USABLE_INGREDIENT);
		}
		rateLimiter.check(String.valueOf(userId));

		List<RecommendedRecipe> recommendedRecipes = recommendationService.recommend(ingredients, prioritizeUrgent);

		return transactionTemplate.execute(status -> save(userId, fridgeId, recommendedRecipes));
	}

	@Transactional(readOnly = true)
	public RecipeResponse getRecipe(Long userId, Long recipeId) {
		// 다른 냉장고의 레시피는 있는지도 알 수 없게 없는 레시피와 같은 오류를 준다
		Recipe recipe = recipeRepository.findById(recipeId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
		return RecipeResponse.of(recipe, recipeIngredientRepository.findAllByRecipeIdInOrderByIdAsc(List.of(recipeId)));
	}

	private RecipeRecommendationResponse save(Long userId, Long fridgeId, List<RecommendedRecipe> recommendedRecipes) {
		Fridge fridge = fridgeRepository.getReferenceById(fridgeId);
		User user = userRepository.getReferenceById(userId);

		List<RecipeResponse> recipes = new ArrayList<>();
		for (RecommendedRecipe recommendedRecipe : recommendedRecipes) {
			Recipe recipe = recipeRepository.save(toRecipe(recommendedRecipe, fridge, user));
			List<RecipeIngredient> ingredients = recipeIngredientRepository.saveAll(
				recommendedRecipe.ingredients().stream()
					.map(ingredient -> RecipeIngredient.builder()
						.recipe(recipe)
						.name(ingredient.name())
						.amount(ingredient.amount())
						.source(ingredient.source())
						.isRequired(ingredient.required())
						.build())
					.toList());
			recipes.add(RecipeResponse.of(recipe, ingredients));
		}
		return new RecipeRecommendationResponse(recipes);
	}

	private Recipe toRecipe(RecommendedRecipe recommendedRecipe, Fridge fridge, User user) {
		return Recipe.builder()
			.fridge(fridge)
			.requestedBy(user)
			.type(RecipeType.RECOMMEND)
			.title(recommendedRecipe.name())
			.emoji(recommendedRecipe.emoji())
			.summary(recommendedRecipe.summary())
			.steps(recommendedRecipe.steps())
			.tips(recommendedRecipe.tips())
			.cookMinutes(recommendedRecipe.cookTimeMinutes())
			.difficulty(recommendedRecipe.difficulty())
			.servings(recommendedRecipe.servings())
			.caloriesKcal(recommendedRecipe.caloriesKcal())
			.proteinG(recommendedRecipe.nutrition().proteinG())
			.carbohydrateG(recommendedRecipe.nutrition().carbohydrateG())
			.fatG(recommendedRecipe.nutrition().fatG())
			.build();
	}

}
