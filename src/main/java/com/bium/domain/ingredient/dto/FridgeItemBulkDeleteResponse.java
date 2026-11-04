package com.bium.domain.ingredient.dto;

import java.util.List;

// 지운 재료 id. 같은 재료를 두 번 보내도 한 번만 담는다
public record FridgeItemBulkDeleteResponse(List<Long> deletedItemIds) {
}
