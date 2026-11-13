package com.bium.domain.feed.dto;

import com.bium.domain.ingredient.entity.FridgeItem;

// 피드 카드에 보여 줄 재료. 아이콘이 없는 재료는 emoji가 null이다
public record FeedItemPreview(String name, String emoji) {

	public static FeedItemPreview from(FridgeItem item) {
		return new FeedItemPreview(item.getName(), item.getEmoji());
	}

}
