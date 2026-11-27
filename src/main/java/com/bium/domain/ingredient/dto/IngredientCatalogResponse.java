package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.StorageType;

// imageUrl은 이모지 대신 올린 사진 주소다. 있으면 화면은 이모지 대신 사진을 보여 준다.
// 기본 재료는 응답을 받는 사용자가 직접 올린 사진만 담긴다(IngredientImageLookup)
// defaultStorage는 냉장고에 넣을 때 처음 골라 둘 보관 위치다. 기본 재료에 정해져 있고, 정해 두지 않은 재료는 null이다
public record IngredientCatalogResponse(Long id, String name, String emoji, String imageUrl,
	IngredientCategory category, StorageType defaultStorage, Integer shelfLifeDays, boolean favorite, boolean basic) {

	public static IngredientCatalogResponse from(IngredientCatalog catalog, boolean favorite,
		IngredientImageLookup images) {
		return new IngredientCatalogResponse(catalog.getId(), catalog.getName(), catalog.getEmoji(),
			images.urlOf(catalog), catalog.getCategory(), catalog.getDefaultStorage(), catalog.getShelfLifeDays(), favorite, catalog.isShared());
	}

}
