package com.bium.domain.shopping.dto;

import java.time.LocalDate;

import com.bium.domain.shopping.entity.PurchasedItem;

public record PurchasedItemResponse(Long id, String name, String emoji, LocalDate expiresAt) {

	public static PurchasedItemResponse from(PurchasedItem item) {
		return new PurchasedItemResponse(item.getId(), item.getName(), item.getEmoji(), item.getExpiresAt());
	}

}
