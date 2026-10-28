package com.bium.domain.receipt.dto;

import java.time.LocalDate;
import java.util.List;

import com.bium.domain.ingredient.entity.StorageType;

/**
 * 영수증에서 읽은 식재료 후보다. 저장하지 않으며, 화면에서 고친 뒤 재료 일괄 추가 API로 넣는다.
 * 아이콘·유통기한을 정하지 못한 재료는 null로 보내 사용자가 채우게 한다.
 */
public record ReceiptScanResponse(List<Item> items) {

	public record Item(String name, String emoji, StorageType storage, LocalDate expiresAt) {
	}

}
