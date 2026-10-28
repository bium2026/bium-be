package com.bium.domain.ingredient.dto;

import java.util.List;

// 요청에 보낸 순서대로 담는다
public record FridgeItemBulkCreateResponse(List<FridgeItemResponse> items) {
}
