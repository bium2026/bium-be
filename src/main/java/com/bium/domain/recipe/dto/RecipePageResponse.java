package com.bium.domain.recipe.dto;

import java.util.List;

// 최신 레시피부터 준다. 다음 페이지가 없으면 nextCursor는 null이고, 있으면 다음 요청의 cursor로 그대로 보낸다
public record RecipePageResponse(List<RecipeResponse> recipes, Long nextCursor) {
}
