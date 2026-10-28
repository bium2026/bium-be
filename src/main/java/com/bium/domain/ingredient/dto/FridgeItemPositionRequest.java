package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 재료를 끌어다 놓은 위치를 받는다. index는 놓을 칸 안에서 0부터 센 위치다.
 * version은 화면이 재료를 불러올 때 받은 값이며, 그 사이 다른 멤버가 고쳤으면 옮기지 않는다.
 */
public record FridgeItemPositionRequest(
	@NotNull(message = "보관 위치를 선택해 주세요.")
	StorageType storage,
	@NotNull(message = "칸을 선택해 주세요.")
	SectionType section,
	@NotNull(message = "놓을 위치를 보내 주세요.")
	@Min(value = 0, message = "놓을 위치는 0 이상이어야 합니다.")
	Integer index,
	@NotNull(message = "재료 버전을 보내 주세요.")
	Integer version
) {
}
