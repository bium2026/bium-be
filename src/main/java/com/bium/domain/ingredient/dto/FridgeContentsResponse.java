package com.bium.domain.ingredient.dto;

import java.util.List;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;

/**
 * 냉장고 화면이 그리는 모양 그대로 보관 구역(냉장실·냉동실·실온)과 칸별로 재료를 나눠 담는다.
 * 재료가 없는 칸도 빈 배열로 내려서 화면이 칸 유무를 따로 확인하지 않게 한다.
 */
public record FridgeContentsResponse(StorageContents fridge, StorageContents freezer, StorageContents room) {

	// items는 칸 안 순서대로 정렬돼 있어야 한다
	public static FridgeContentsResponse from(List<FridgeItem> items) {
		return new FridgeContentsResponse(
			StorageContents.of(items, StorageType.FRIDGE),
			StorageContents.of(items, StorageType.FREEZER),
			StorageContents.of(items, StorageType.ROOM));
	}

	public record StorageContents(
		List<FridgeItemResponse> center,
		List<FridgeItemResponse> leftDoor,
		List<FridgeItemResponse> rightDoor
	) {

		private static StorageContents of(List<FridgeItem> items, StorageType storage) {
			return new StorageContents(
				filter(items, storage, SectionType.CENTER),
				filter(items, storage, SectionType.LEFT_DOOR),
				filter(items, storage, SectionType.RIGHT_DOOR));
		}

		private static List<FridgeItemResponse> filter(List<FridgeItem> items, StorageType storage,
			SectionType section) {
			return items.stream()
				.filter(item -> item.getStorage() == storage && item.getSection() == section)
				.map(FridgeItemResponse::from)
				.toList();
		}

	}

}
