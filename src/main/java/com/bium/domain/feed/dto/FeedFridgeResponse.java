package com.bium.domain.feed.dto;

/**
 * 피드에 보이는 공개 냉장고 하나.
 * expiringCount는 오늘부터 3일 안에 유통기한이 끝나는 재료 수이고, mine은 내가 소유자나 멤버인 냉장고인지다.
 * isPublic은 피드 목록에서는 늘 true이고, 멤버가 공개하지 않은 내 냉장고를 미리 볼 때만 false다.
 */
public record FeedFridgeResponse(
	Long fridgeId,
	String name,
	boolean isPublic,
	String ownerNickname,
	int memberCount,
	int itemCount,
	int expiringCount,
	FeedStorages storages,
	long likeCount,
	boolean liked,
	boolean saved,
	boolean mine
) {
}
