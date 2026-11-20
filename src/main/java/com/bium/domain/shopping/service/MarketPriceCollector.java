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

	// 주말에 공휴일이 붙어도 가격이 있는 날까지 거슬러 갈 수 있는 날 수
	private static final int MAX_DAYS_BACK = 7;

	private final KamisClient kamisClient;
	private final MarketPriceService marketPriceService;
	private final Clock clock;

	@Scheduled(cron = "${kamis.collect-cron}", zone = "Asia/Seoul")
	public void collect() {
		LocalDate today = LocalDate.now(clock);
		int saved = marketPriceService.save(today, fetch(today));
		log.info("KAMIS 소매가를 저장했습니다. date={}, items={}", today, saved);
	}

	/**
	 * 저장된 시세가 하나도 없을 때만 가장 최근에 조사한 날의 가격을 받는다.
	 * 처음 켠 서버도 18시 예약 작업을 기다리지 않고 장보기 시세를 보여 주려는 것이다.
	 * 오늘 가격이 아직 안 올라왔거나 주말·공휴일이면 가격이 있는 날이 나올 때까지 하루씩 거슬러 간다.
	 */
	public void collectLatestIfEmpty() {
		if (marketPriceService.hasAny()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		for (int daysBack = 0; daysBack < MAX_DAYS_BACK; daysBack++) {
			LocalDate day = today.minusDays(daysBack);
			List<KamisPrice> prices = fetch(day);
			// 한 줄도 오지 않았으면 KAMIS 호출이 실패한 것이라 다른 날을 불러도 같다
			if (prices.isEmpty()) {
				log.warn("KAMIS에서 가격을 받지 못해 서버를 켤 때 시세를 채우지 못했습니다. date={}", day);
				return;
			}
			int saved = marketPriceService.save(day, prices);
			if (saved > 0) {
				log.info("서버를 켜며 KAMIS 소매가를 저장했습니다. date={}, items={}", day, saved);
				return;
			}
		}
		log.warn("최근 {}일 동안 조사한 KAMIS 소매가가 없어 시세를 채우지 못했습니다.", MAX_DAYS_BACK);
	}

	private List<KamisPrice> fetch(LocalDate day) {
		List<KamisPrice> prices = new ArrayList<>();
		for (String categoryCode : CATEGORY_CODES) {
			prices.addAll(kamisClient.fetchRetailPrices(categoryCode, day));
		}
		return prices;
	}

}
