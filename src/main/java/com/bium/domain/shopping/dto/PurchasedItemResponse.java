package com.bium.domain.shopping.dto;

import java.time.LocalDate;

import com.bium.domain.ingredient.dto.IngredientImageLookup;
import com.bium.domain.shopping.entity.PurchasedItem;

// imageUrl은 이 음식을 담은 도감 재료의 사진 주소다(IngredientImageLookup). 없으면 null이고 화면은 emoji를 보여 준다
public record PurchasedItemResponse(Long id, String name, String emoji, String imageUrl, LocalDate expiresAt) {

	public static PurchasedItemResponse from(PurchasedItem item, IngredientImageLookup images) {
		return new PurchasedItemResponse(item.getId(), item.getName(), item.getEmoji(),
			images.urlOf(item.getCatalog()), item.getExpiresAt());
	}

}
