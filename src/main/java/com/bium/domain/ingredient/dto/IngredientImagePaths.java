package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;

// 응답에 담을 재료 사진 주소. 화면은 서버 주소 뒤에 붙여 <img src>로 쓴다. 사진이 없으면 null이다
public final class IngredientImagePaths {

	public static final String PREFIX = "/api/ingredient-images/";

	private IngredientImagePaths() {
	}

	public static String of(IngredientCatalog catalog) {
		return catalog == null || catalog.getImageKey() == null ? null : PREFIX + catalog.getImageKey();
	}

	public static String of(FridgeItem item) {
		return of(item.getCatalog());
	}

}
