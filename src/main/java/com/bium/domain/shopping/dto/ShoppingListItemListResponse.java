package com.bium.domain.shopping.dto;

import java.util.List;
import java.util.Map;

import com.bium.domain.shopping.entity.MarketPrice;
import com.bium.domain.shopping.entity.ShoppingListItem;

public record ShoppingListItemListResponse(List<ShoppingListItemResponse> items) {

	// prices는 재료 이름으로 찾은 시세다
	public static ShoppingListItemListResponse of(List<ShoppingListItem> items, Map<String, MarketPrice> prices) {
		return new ShoppingListItemListResponse(items.stream()
			.map(item -> ShoppingListItemResponse.of(item, prices.get(item.getName())))
			.toList());
	}

}
