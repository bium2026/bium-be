package com.bium.domain.shopping.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 재료별 다음 구매 날짜. 다음 구매일이 이미 지났어도 그대로 준다.
 *
 * @param lastPurchasedAt 마지막으로 산 날
 * @param purchaseCount 서로 다른 구매일 수. 같은 날 여러 번 넣은 것은 한 번으로 센다
 * @param cycleDays 구매 주기(일)
 */
public record PurchaseScheduleResponse(List<Item> items) {

	public record Item(
		String name,
		String emoji,
		LocalDate lastPurchasedAt,
		int purchaseCount,
		int cycleDays,
		LocalDate nextPurchaseDate
	) {
	}

}
