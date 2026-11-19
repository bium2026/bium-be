package com.bium.domain.shopping.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서버를 켤 때 시세가 비어 있으면 바로 채운다. 테스트에서는 kamis.collect-on-startup=false로 끈다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "kamis", name = "collect-on-startup", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MarketPriceStartupRunner implements ApplicationRunner {

	private final MarketPriceCollector marketPriceCollector;

	@Override
	public void run(ApplicationArguments args) {
		try {
			marketPriceCollector.collectLatestIfEmpty();
		} catch (DataAccessException exception) {
			// 시세는 보조 정보라서 채우지 못해도 서버는 켜고 18시 예약 작업에 맡긴다
			log.error("서버를 켜며 KAMIS 소매가를 저장하지 못했습니다.", exception);
		}
	}

}
