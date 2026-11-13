package com.bium.domain.feed.dto;

import java.util.List;

// 다음 페이지가 없으면 nextCursor는 null이다. 있으면 다음 요청의 cursor로 그대로 보낸다
public record FeedPageResponse(List<FeedFridgeResponse> fridges, Long nextCursor) {
}
