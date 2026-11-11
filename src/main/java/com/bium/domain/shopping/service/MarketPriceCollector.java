package com.bium.domain.shopping.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bium.global.kamis.KamisClient;
import com.bium.global.kamis.KamisPrice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 하루 한 번 KAMIS 소매가를 받아 저장한다. 장보기 목록을 열 때마다 KAMIS를 부르지 않으려는 것이다.
 * 조사하지 않는 주말·공휴일에는 받은 가격이 없어 전날 가격이 그대로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceCollector {

	// 식량작물, 채소, 특용작물, 과일, 축산물, 수산물
	private static final List<String> CATEGORY_CODES = List.of("100", "200", "300", "400", "500", "600");

	private final KamisClient kamisClient;
	private final MarketPriceService marketPriceService;
	private final Clock clock;

	@Scheduled(cron = "${kamis.collect-cron}", zone = "Asia/Seoul")
	public void collect() {
		LocalDate today = LocalDate.now(clock);
		List<KamisPrice> prices = new ArrayList<>();
		for (String categoryCode : CATEGORY_CODES) {
			prices.addAll(kamisClient.fetchRetailPrices(categoryCode, today));
		}
		int saved = marketPriceService.save(today, prices);
		log.info("KAMIS 소매가를 저장했습니다. date={}, items={}", today, saved);
	}

}
