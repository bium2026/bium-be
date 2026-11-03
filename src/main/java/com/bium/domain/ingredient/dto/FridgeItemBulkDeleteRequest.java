package com.bium.domain.ingredient.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 유통기한 지난 재료처럼 여러 재료를 한 번에 지울 때 받는다. 하나라도 지울 수 없으면 아무것도 지우지 않는다.
 * version은 화면이 재료를 불러올 때 받은 값이며, 그 사이 다른 멤버가 고친 재료가 있으면 지우지 않는다.
 */
public record FridgeItemBulkDeleteRequest(
	@NotEmpty(message = "지울 재료를 하나 이상 보내 주세요.")
	@Size(max = 100, message = "재료는 한 번에 100개까지 지울 수 있습니다.")
	List<@NotNull(message = "재료 정보를 보내 주세요.") @Valid Item> items
) {

	public record Item(
		@NotNull(message = "재료를 선택해 주세요.")
		Long fridgeItemId,
		@NotNull(message = "재료 버전을 보내 주세요.")
		Integer version
	) {
	}

}
