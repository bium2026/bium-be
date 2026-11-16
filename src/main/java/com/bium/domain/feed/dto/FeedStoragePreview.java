package com.bium.domain.feed.dto;

import java.util.List;

// 보관 구역 하나의 재료 수와 앞에서부터 몇 개의 재료 (선반 → 왼쪽 문칸 → 오른쪽 문칸, 칸 안 순서)
public record FeedStoragePreview(int count, List<FeedItemPreview> items) {
}
