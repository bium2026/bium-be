package com.bium.domain.recipe.service;

import org.springframework.stereotype.Component;

import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.repository.RecipeRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 레시피를 그 레시피 냉장고의 멤버에게만 찾아 준다.
 */
@Component
@RequiredArgsConstructor
public class RecipeFinder {

	private final RecipeRepository recipeRepository;
	private final FridgeAccessValidator fridgeAccessValidator;

	// 다른 냉장고의 레시피는 있는지도 알 수 없게 없는 레시피와 같은 오류를 준다
	public Recipe findAccessible(Long userId, Long recipeId) {
		return recipeRepository.findById(recipeId)
			.filter(recipe -> fridgeAccessValidator.isMember(recipe.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
	}

}
