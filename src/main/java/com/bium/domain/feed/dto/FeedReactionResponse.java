package com.bium.domain.feed.dto;

// 좋아요·저장을 바꾼 뒤의 좋아요 수와 내 좋아요·저장 여부
public record FeedReactionResponse(long likeCount, boolean liked, boolean saved) {
}
