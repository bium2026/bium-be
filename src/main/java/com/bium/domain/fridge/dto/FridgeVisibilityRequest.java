package com.bium.domain.fridge.dto;

import jakarta.validation.constraints.NotNull;

// 냉장고를 피드에 공개할지 받는다. true면 다른 사용자가 피드에서 재료를 볼 수 있다
public record FridgeVisibilityRequest(
	@NotNull(message = "공개 여부를 보내 주세요.")
	Boolean isPublic
) {
}
