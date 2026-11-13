package com.bium.domain.feed.dto;

import com.bium.domain.ingredient.dto.FridgeContentsResponse;

// 피드에서 구경하는 공개 냉장고. contents는 냉장고 재료 조회와 같은 모양으로 칸별 재료를 모두 담는다
public record FeedFridgeDetailResponse(FeedFridgeResponse fridge, FridgeContentsResponse contents) {
}
