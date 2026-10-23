package com.bium.domain.shopping.dto;

import jakarta.validation.constraints.NotNull;

public record PurchasedItemCreateRequest(
	@NotNull(message = "도감 재료를 선택해 주세요.")
	Long catalogId
) {
}
