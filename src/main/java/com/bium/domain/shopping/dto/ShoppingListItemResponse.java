package com.bium.domain.shopping.dto;

import java.time.LocalDateTime;

import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.shopping.entity.MarketPrice;
import com.bium.domain.shopping.entity.ShoppingListItem;

// 직접 담은 재료는 recipeId와 recipeName이 null이다. 시세가 없는 재료는 marketPrice가 null이다
public record ShoppingListItemResponse(Long id, String name, boolean checked, Long recipeId, String recipeName,
	LocalDateTime createdAt, MarketPriceResponse marketPrice) {

	public static ShoppingListItemResponse of(ShoppingListItem item, MarketPrice marketPrice) {
		Recipe recipe = item.getRecipe();
		return new ShoppingListItemResponse(item.getId(), item.getName(), item.isChecked(),
			recipe == null ? null : recipe.getId(), recipe == null ? null : recipe.getTitle(), item.getCreatedAt(),
			MarketPriceResponse.from(marketPrice));
	}

}
