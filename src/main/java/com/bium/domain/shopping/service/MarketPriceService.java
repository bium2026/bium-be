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

	// 품종마다 상품·중품 가격이 따로 오는데, 장보기 목록에는 '상품' 등급 하나만 보여 준다
	private static final String GRADE = "상품";

	private final MarketPriceRepository marketPriceRepository;

	/**
	 * 받은 가격 중 품목마다 처음 나온 '상품' 등급 가격을 대표값으로 저장한다.
	 * 같은 날 다시 받아도 같은 행을 덮어쓰므로 결과가 같다.
	 */
	public int save(LocalDate surveyDate, List<KamisPrice> prices) {
		Map<String, KamisPrice> representatives = new LinkedHashMap<>();
		for (KamisPrice price : prices) {
			if (GRADE.equals(price.rank()) && price.price() != null) {
				representatives.putIfAbsent(price.itemName(), price);
			}
		}
		if (representatives.isEmpty()) {
			return 0;
		}

		Map<String, MarketPrice> saved = findByItemNames(representatives.keySet());
		representatives.forEach((itemName, price) -> {
			MarketPrice marketPrice = saved.get(itemName);
			if (marketPrice == null) {
				marketPriceRepository.save(MarketPrice.builder()
					.itemName(itemName)
					.kindName(price.kindName())
					.unit(price.unit())
					.price(price.price())
					.weekAgoPrice(price.weekAgoPrice())
					.surveyDate(surveyDate)
					.build());
			} else {
				marketPrice.update(price.kindName(), price.unit(), price.price(), price.weekAgoPrice(), surveyDate);
			}
		});
		return representatives.size();
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
