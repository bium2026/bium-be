package com.bium.domain.feed.dto;

// fridge: 냉장실, freezer: 냉동실, room: 실온 (냉장고 재료 조회 응답과 같은 이름)
public record FeedStorages(FeedStoragePreview fridge, FeedStoragePreview freezer, FeedStoragePreview room) {
}
