package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;

// imageUrl은 이모지 대신 올린 사진 주소다. 있으면 화면은 이모지 대신 사진을 보여 준다
public record IngredientCatalogResponse(Long id, String name, String emoji, String imageUrl,
	IngredientCategory category, Integer shelfLifeDays, boolean favorite, boolean basic) {

	public static IngredientCatalogResponse from(IngredientCatalog catalog, boolean favorite) {
		return new IngredientCatalogResponse(catalog.getId(), catalog.getName(), catalog.getEmoji(),
			IngredientImagePaths.of(catalog), catalog.getCategory(), catalog.getShelfLifeDays(), favorite,
			catalog.isShared());
	}

}
