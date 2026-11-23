package com.bium.domain.shopping.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.shopping.dto.PurchaseScheduleResponse;

import lombok.RequiredArgsConstructor;

/**
 * 재료마다 이 냉장고에 넣은 날로 다음 구매 날짜를 계산한다. 지운·다 쓴 재료도 구매 기록으로 센다.
 */
@Service
@RequiredArgsConstructor
public class PurchaseScheduleService {

	private final FridgeItemRepository fridgeItemRepository;
	private final FridgeAccessValidator fridgeAccessValidator;

	/**
	 * 구매일이 있는 재료를 도감 이름(없으면 재료 이름)으로 묶어 계산한다.
	 * 구매일이 둘 이상이면 (마지막 - 처음) / (구매일 수 - 1)을 반올림한 일수가 주기이고(최소 1일),
	 * 하나뿐이면 가장 최근 재료의 구매 주기를 쓴다. 그것도 없으면 그 재료는 뺀다.
	 */
	@Transactional(readOnly = true)
	public PurchaseScheduleResponse getSchedule(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);

		// 구매일, id 순서로 읽으므로 각 묶음의 마지막 재료가 가장 최근에 넣은 재료다
		Map<String, List<FridgeItem>> groups = new LinkedHashMap<>();
		for (FridgeItem item : fridgeItemRepository.findAllPurchasedWithCatalogByFridgeId(fridgeId)) {
			groups.computeIfAbsent(groupName(item), name -> new ArrayList<>()).add(item);
		}

		List<PurchaseScheduleResponse.Item> items = new ArrayList<>();
		groups.forEach((name, rows) -> toItem(name, rows, items));
		items.sort(Comparator.comparing(PurchaseScheduleResponse.Item::nextPurchaseDate)
			.thenComparing(PurchaseScheduleResponse.Item::name));
		return new PurchaseScheduleResponse(items);
	}

	private void toItem(String name, List<FridgeItem> rows, List<PurchaseScheduleResponse.Item> items) {
		FridgeItem latest = rows.get(rows.size() - 1);
		TreeSet<LocalDate> dates = new TreeSet<>();
		rows.forEach(row -> dates.add(row.getPurchasedAt()));

		int count = dates.size();
		int cycleDays;
		if (count >= 2) {
			long days = ChronoUnit.DAYS.between(dates.first(), dates.last());
			// 정수로 반올림한다(0.5는 올림)
			cycleDays = (int) Math.max(1, (2 * days + (count - 1)) / (2L * (count - 1)));
		} else if (latest.getPurchaseCycleDays() != null && latest.getPurchaseCycleDays() > 0) {
			cycleDays = latest.getPurchaseCycleDays();
		} else {
			return;
		}
		LocalDate last = dates.last();
		items.add(new PurchaseScheduleResponse.Item(name, latest.getEmoji(), last, count, cycleDays,
			last.plusDays(cycleDays)));
	}

	private String groupName(FridgeItem item) {
		return item.getCatalog() != null ? item.getCatalog().getName() : item.getName().trim();
	}

}
