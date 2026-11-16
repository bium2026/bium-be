package com.bium.domain.shopping.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.shopping.entity.MarketPrice;
import com.bium.domain.shopping.repository.MarketPriceRepository;
import com.bium.global.kamis.KamisPrice;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MarketPriceService {

	// 사람들이 흔히 쓰는 재료 이름 → KAMIS 품목명. KAMIS는 '파', '돼지'처럼 다른 이름을 써서 그대로는 맞지 않는다.
	// 삼겹살처럼 부위 이름은 넣지 않는다. 품목 최저가가 다른 부위(앞다리) 가격이라 잘못 보이기 때문이다
	private static final Map<String, String> KAMIS_NAME_ALIASES = Map.ofEntries(
		Map.entry("대파", "파"),
		Map.entry("쪽파", "파"),
		Map.entry("마늘", "깐마늘(국산)"),
		Map.entry("깐마늘", "깐마늘(국산)"),
		Map.entry("청양고추", "풋고추"),
		Map.entry("꽈리고추", "풋고추"),
		Map.entry("홍고추", "붉은고추"),
		Map.entry("귤", "감귤"),
		Map.entry("돼지고기", "돼지"),
		Map.entry("소고기", "소"),
		Map.entry("쇠고기", "소"),
		Map.entry("한우", "소"),
		Map.entry("닭고기", "닭"),
		Map.entry("달걀", "계란"),
		Map.entry("오징어", "물오징어"),
		Map.entry("멸치", "마른멸치"),
		Map.entry("미역", "마른미역"),
		Map.entry("다시마", "건다시마"),
		Map.entry("소금", "천일염"));

	private final MarketPriceRepository marketPriceRepository;

	/**
	 * 받은 가격 중 품목마다 품종·등급을 통틀어 가장 싼 가격을 저장한다.
	 * 단위가 다르면(사과 10개와 1개처럼) 비교할 수 없어서, 품목에서 처음 나온 가격의 단위와 같은 것끼리만 비교한다.
	 * 같은 날 다시 받아도 같은 행을 덮어쓰므로 결과가 같다.
	 */
	public int save(LocalDate surveyDate, List<KamisPrice> prices) {
		Map<String, KamisPrice> cheapest = new LinkedHashMap<>();
		for (KamisPrice price : prices) {
			if (price.price() == null) {
				continue;
			}
			KamisPrice current = cheapest.get(price.itemName());
			if (current == null
				|| (current.unit().equals(price.unit()) && price.price() < current.price())) {
				cheapest.put(price.itemName(), price);
			}
		}
		if (cheapest.isEmpty()) {
			return 0;
		}

		Map<String, MarketPrice> saved = findSaved(cheapest.keySet());
		cheapest.forEach((itemName, price) -> {
			MarketPrice marketPrice = saved.get(itemName);
			if (marketPrice == null) {
				marketPriceRepository.save(MarketPrice.builder()
					.itemName(itemName)
					.kindName(price.kindName())
					.grade(price.rank())
					.unit(price.unit())
					.price(price.price())
					.weekAgoPrice(price.weekAgoPrice())
					.surveyDate(surveyDate)
					.build());
			} else {
				marketPrice.update(price.kindName(), price.rank(), price.unit(), price.price(), price.weekAgoPrice(), surveyDate);
			}
		});
		return cheapest.size();
	}

	/**
	 * 재료 이름으로 시세를 찾는다. 돌려주는 Map의 키는 넘겨받은 이름 그대로다.
	 * 목록의 이름들을 한 번에 조회해서 항목마다 쿼리가 나가지 않게 한다.
	 */
	@Transactional(readOnly = true)
	public Map<String, MarketPrice> findByItemNames(Collection<String> itemNames) {
		if (itemNames.isEmpty()) {
			return Map.of();
		}
		Map<String, MarketPrice> byKamisName = findSaved(
			itemNames.stream().map(MarketPriceService::toKamisName).collect(Collectors.toSet()));
		Map<String, MarketPrice> result = new HashMap<>();
		for (String itemName : itemNames) {
			MarketPrice marketPrice = byKamisName.get(toKamisName(itemName));
			if (marketPrice != null) {
				result.put(itemName, marketPrice);
			}
		}
		return result;
	}

	private Map<String, MarketPrice> findSaved(Collection<String> kamisNames) {
		return marketPriceRepository.findAllByItemNameIn(kamisNames).stream()
			.collect(Collectors.toMap(MarketPrice::getItemName, Function.identity()));
	}

	private static String toKamisName(String itemName) {
		return KAMIS_NAME_ALIASES.getOrDefault(itemName, itemName);
	}

}
