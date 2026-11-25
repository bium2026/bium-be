package com.bium.domain.ingredient.dto;

import java.time.LocalDate;

import com.bium.domain.ingredient.entity.FridgeItem;

// purchasedAt은 냉장고에 넣은(산) 날이며, 화면이 유통기한까지 얼마나 지났는지 막대로 보여 줄 때 쓴다. 모르면 null이다
// imageUrl은 이 재료를 넣은 도감 재료에 올린 사진 주소다. 없으면 null이고 화면은 emoji를 보여 준다
public record FridgeItemResponse(Long id, String name, String emoji, String imageUrl, LocalDate expiresAt,
	LocalDate purchasedAt, Integer version) {

	public static FridgeItemResponse from(FridgeItem item) {
		return new FridgeItemResponse(item.getId(), item.getName(), item.getEmoji(), IngredientImagePaths.of(item),
			item.getExpiresAt(), item.getPurchasedAt(), item.getVersion());
	}

}
