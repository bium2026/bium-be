package com.bium.domain.recipe.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 요리에 쓴 냉장고 재료를 받는다. 화면에서 확인한 재료만 보내며, 보낸 재료는 모두 냉장고에서 빠진다.
 * version은 재료를 불러올 때 받은 값이며, 그 사이 다른 멤버가 고쳤으면 아무 재료도 빼지 않는다.
 * 냉장고에서 뺄 재료가 없어도 요리한 기록은 남길 수 있어 빈 목록을 받는다.
 */
public record CookingRequest(
	@NotNull(message = "요리에 쓴 재료 목록을 보내 주세요.")
	@Size(max = 50, message = "재료는 한 번에 50개까지 뺄 수 있습니다.")
	List<@Valid @NotNull(message = "재료를 보내 주세요.") UsedItem> items
) {

	public record UsedItem(
		@NotNull(message = "재료를 선택해 주세요.")
		Long fridgeItemId,
		@NotNull(message = "재료 버전을 보내 주세요.")
		Integer version
	) {
	}

}
