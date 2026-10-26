package com.bium.domain.recipe.dto;

import java.time.LocalDateTime;
import java.util.List;

// 되돌린 기록 id와 냉장고에 다시 살아난 재료 id
public record CookingUndoResponse(Long id, List<Long> restoredItemIds, LocalDateTime undoneAt) {
}
