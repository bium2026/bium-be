package com.bium.domain.shopping.dto;

import java.time.LocalDate;

import com.bium.domain.ingredient.dto.IngredientImagePaths;
import com.bium.domain.shopping.entity.PurchasedItem;

// imageUrl은 이 음식을 담은 도감 재료에 올린 사진 주소다. 없으면 null이고 화면은 emoji를 보여 준다
public record PurchasedItemResponse(Long id, String name, String emoji, String imageUrl, LocalDate expiresAt) {

	public static PurchasedItemResponse from(PurchasedItem item) {
		return new PurchasedItemResponse(item.getId(), item.getName(), item.getEmoji(),
			IngredientImagePaths.of(item.getCatalog()), item.getExpiresAt());
	}

}
