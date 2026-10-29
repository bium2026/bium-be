package com.bium.domain.shopping.dto;

import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 장본 음식을 끌어다 놓은 냉장고 위치를 받는다. index는 놓을 칸 안에서 0부터 센 위치다
public record PurchasedItemMoveRequest(
	@NotNull(message = "보관 위치를 선택해 주세요.")
	StorageType storage,
	@NotNull(message = "칸을 선택해 주세요.")
	SectionType section,
	@NotNull(message = "놓을 위치를 보내 주세요.")
	@Min(value = 0, message = "놓을 위치는 0 이상이어야 합니다.")
	Integer index
) {
}
