package com.bium.domain.shopping.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.recipe.recommendation.IngredientMatcher;
import com.bium.domain.recipe.repository.RecipeIngredientRepository;
import com.bium.domain.recipe.repository.RecipeRepository;
import com.bium.domain.shopping.dto.MissingIngredientAddRequest;
import com.bium.domain.shopping.dto.ShoppingListItemListResponse;
import com.bium.domain.shopping.dto.ShoppingListItemResponse;
import com.bium.domain.shopping.entity.ShoppingListItem;
import com.bium.domain.shopping.repository.ShoppingListItemRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 레시피의 부족 재료 중 사용자가 확인 모달에서 고른 재료를 그 레시피 냉장고의 장보기 목록에 담는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MissingIngredientService {

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final ShoppingListItemRepository shoppingListItemRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;

	// 고른 재료 중 하나라도 이 레시피의 부족 재료가 아니면 아무것도 담지 않는다
	public ShoppingListItemListResponse add(Long userId, Long recipeId, MissingIngredientAddRequest request) {
		// 다른 냉장고의 레시피는 있는지도 알 수 없게 없는 레시피와 같은 오류를 준다
		Recipe recipe = recipeRepository.findById(recipeId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

		Set<String> missingNames = recipeIngredientRepository.findAllByRecipeIdInOrderByIdAsc(List.of(recipeId))
			.stream()
			.filter(ingredient -> ingredient.getSource() == IngredientSource.MISSING)
			.map(RecipeIngredient::getName)
			.collect(Collectors.toSet());
		// 같은 재료를 두 번 보내도 한 번만 담는다
		List<String> names = request.names().stream().map(String::strip).distinct().toList();
		if (!missingNames.containsAll(names)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		// 아직 안 산 같은 재료가 목록에 있으면 다시 담지 않는다. 이미 산 재료는 또 살 수 있어 다시 담는다
		List<String> uncheckedNames = shoppingListItemRepository
			.findAllByFridgeIdAndIsCheckedFalse(recipe.getFridge().getId()).stream()
			.map(ShoppingListItem::getName)
			.toList();
		List<ShoppingListItem> items = names.stream()
			.filter(name -> uncheckedNames.stream().noneMatch(unchecked -> IngredientMatcher.matches(name, unchecked)))
			.map(name -> ShoppingListItem.builder()
				.fridge(recipe.getFridge())
				.createdBy(userRepository.getReferenceById(userId))
				.recipe(recipe)
				.name(name)
				.build())
			.toList();
		shoppingListItemRepository.saveAll(items);
		return new ShoppingListItemListResponse(items.stream().map(ShoppingListItemResponse::from).toList());
	}

}
