package com.bium.domain.shopping.dto;

import jakarta.validation.constraints.NotNull;

public record ShoppingListItemCheckRequest(
	@NotNull(message = "샀는지 여부를 보내 주세요.")
	Boolean checked
) {
}
