package com.bium.domain.ingredient.dto;

import java.time.LocalDate;

import com.bium.domain.ingredient.entity.FridgeItem;

public record FridgeItemResponse(Long id, String name, String emoji, LocalDate expiresAt, Integer version) {

	public static FridgeItemResponse from(FridgeItem item) {
		return new FridgeItemResponse(item.getId(), item.getName(), item.getEmoji(), item.getExpiresAt(),
			item.getVersion());
	}

}
