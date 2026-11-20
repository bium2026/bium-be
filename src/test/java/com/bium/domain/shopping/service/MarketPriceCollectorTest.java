package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.shopping.entity.MarketPrice;
import com.bium.global.kamis.KamisClient;
import com.bium.global.kamis.KamisPrice;

@SpringBootTest
@Transactional
class MarketPriceCollectorTest {

	@MockitoBean
	private KamisClient kamisClient;

	@Autowired
	private MarketPriceCollector marketPriceCollector;

	@Autowired
	private MarketPriceService marketPriceService;

	@Autowired
	private Clock clock;

	private LocalDate today;

	@BeforeEach
	void setUp() {
		today = LocalDate.now(clock);
		given(kamisClient.fetchRetailPrices(anyString(), any())).willReturn(List.of());
	}

	@Test
	void 시세가_비어_있으면_가격이_있는_날까지_거슬러_가서_저장한다() {
		// 오늘은 아직 가격이 안 올라와 줄만 오고, 어제 가격이 있다
		given(kamisClient.fetchRetailPrices("200", today))
			.willReturn(List.of(new KamisPrice("양파", "양파(1kg)", "상품", "1kg", null, null)));
		given(kamisClient.fetchRetailPrices("200", today.minusDays(1)))
			.willReturn(List.of(new KamisPrice("양파", "양파(1kg)", "상품", "1kg", 1946, 1893)));

		marketPriceCollector.collectLatestIfEmpty();

		MarketPrice onion = marketPriceService.findByItemNames(List.of("양파")).get("양파");
		assertThat(onion.getPrice()).isEqualTo(1946);
		assertThat(onion.getSurveyDate()).isEqualTo(today.minusDays(1));
		verify(kamisClient, never()).fetchRetailPrices(anyString(), eq(today.minusDays(2)));
	}

	@Test
	void KAMIS_호출이_실패하면_다른_날을_부르지_않는다() {
		marketPriceCollector.collectLatestIfEmpty();

		assertThat(marketPriceService.hasAny()).isFalse();
		verify(kamisClient, times(6)).fetchRetailPrices(anyString(), eq(today));
		verify(kamisClient, never()).fetchRetailPrices(anyString(), eq(today.minusDays(1)));
	}

	@Test
	void 저장된_시세가_있으면_부르지_않는다() {
		marketPriceService.save(today.minusDays(3), List.of(new KamisPrice("양파", "양파", "상품", "1kg", 2000, null)));

		marketPriceCollector.collectLatestIfEmpty();

		verifyNoInteractions(kamisClient);
	}

}
