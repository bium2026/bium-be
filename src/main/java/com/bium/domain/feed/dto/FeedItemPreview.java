package com.bium.domain.feed.dto;

import com.bium.domain.ingredient.dto.IngredientImageLookup;
import com.bium.domain.ingredient.entity.FridgeItem;

// 피드 카드에 보여 줄 재료. 아이콘이 없는 재료는 emoji가 null이고, 사진을 올린 도감 재료로 넣었으면 imageUrl이 있다
public record FeedItemPreview(String name, String emoji, String imageUrl) {

	public static FeedItemPreview from(FridgeItem item, IngredientImageLookup images) {
		return new FeedItemPreview(item.getName(), item.getEmoji(), images.urlOf(item));
	}

}
