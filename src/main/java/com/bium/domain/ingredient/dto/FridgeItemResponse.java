package com.bium.domain.ingredient.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bium.domain.ingredient.entity.FridgeItem;

// purchasedAt은 냉장고에 넣은(산) 날이며, 화면이 유통기한까지 얼마나 지났는지 막대로 보여 줄 때 쓴다. 모르면 null이다
// imageUrl은 이 재료를 넣은 도감 재료의 사진 주소다(IngredientImageLookup). 없으면 null이고 화면은 emoji를 보여 준다
// 수량을 세지 않는 재료("있음")는 quantity·unit이 null이다
public record FridgeItemResponse(Long id, String name, String emoji, String imageUrl, LocalDate expiresAt,
	LocalDate purchasedAt, BigDecimal quantity, String unit, Integer version) {

	public static FridgeItemResponse from(FridgeItem item, IngredientImageLookup images) {
		return new FridgeItemResponse(item.getId(), item.getName(), item.getEmoji(), images.urlOf(item),
			item.getExpiresAt(), item.getPurchasedAt(), item.hasQuantity() ? item.getQuantity() : null,
			item.getUnit(), item.getVersion());
	}

}
