package com.bium.domain.shopping.service;

import java.time.LocalDate;
import java.util.Collection;
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

		Map<String, MarketPrice> saved = findByItemNames(cheapest.keySet());
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

	// 목록의 이름들을 한 번에 조회해서 항목마다 쿼리가 나가지 않게 한다
	@Transactional(readOnly = true)
	public Map<String, MarketPrice> findByItemNames(Collection<String> itemNames) {
		if (itemNames.isEmpty()) {
			return Map.of();
		}
		return marketPriceRepository.findAllByItemNameIn(itemNames).stream()
			.collect(Collectors.toMap(MarketPrice::getItemName, Function.identity()));
	}

}
