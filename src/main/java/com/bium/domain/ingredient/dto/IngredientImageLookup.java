package com.bium.domain.ingredient.dto;

import java.util.Map;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;

/**
 * 응답을 받는 사용자의 화면에 보여 줄 재료 사진 주소를 정한다. 사진이 없으면 null이고 화면은 이모지를 보여 준다.
 * 내가 추가한 재료(주인이 있는 재료)는 주인이 올린 사진을 그 재료를 보는 누구에게나 보여 준다.
 * 기본 재료는 같이 쓰는 재료라서, 응답을 받는 사용자가 직접 올린 사진만 보여 준다.
 * 화면은 주소 앞에 서버 주소를 붙여 <img src>로 쓴다.
 */
public final class IngredientImageLookup {

	public static final String PREFIX = "/api/ingredient-images/";
	public static final IngredientImageLookup NONE = new IngredientImageLookup(Map.of());

	// 기본 재료 id → 응답을 받는 사용자가 올린 사진의 키
	private final Map<Long, String> myBasicImageKeys;

	public IngredientImageLookup(Map<Long, String> myBasicImageKeys) {
		this.myBasicImageKeys = myBasicImageKeys;
	}

	public static String url(String imageKey) {
		return imageKey == null ? null : PREFIX + imageKey;
	}

	public String urlOf(IngredientCatalog catalog) {
		if (catalog == null) {
			return null;
		}
		return url(catalog.isShared() ? myBasicImageKeys.get(catalog.getId()) : catalog.getImageKey());
	}

	public String urlOf(FridgeItem item) {
		return urlOf(item.getCatalog());
	}

}
