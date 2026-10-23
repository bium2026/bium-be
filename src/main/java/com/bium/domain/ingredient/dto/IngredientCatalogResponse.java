package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;

public record IngredientCatalogResponse(Long id, String name, String emoji, IngredientCategory category,
	Integer shelfLifeDays) {

	public static IngredientCatalogResponse from(IngredientCatalog catalog) {
		return new IngredientCatalogResponse(catalog.getId(), catalog.getName(), catalog.getEmoji(),
			catalog.getCategory(), catalog.getShelfLifeDays());
	}

}
