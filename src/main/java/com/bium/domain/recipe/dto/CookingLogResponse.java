package com.bium.domain.recipe.dto;

import java.time.LocalDateTime;
import java.util.List;

// 되돌리기에 쓸 기록 id와 냉장고에서 뺀 재료 id
public record CookingLogResponse(Long id, Long recipeId, List<Long> deductedItemIds, LocalDateTime cookedAt) {
}
