package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.shopping.entity.MarketPrice;
import com.bium.domain.shopping.repository.MarketPriceRepository;
import com.bium.global.kamis.KamisPrice;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class MarketPriceServiceTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
	private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);

	@Autowired
	private MarketPriceService marketPriceService;

	@Autowired
	private MarketPriceRepository marketPriceRepository;

	@Autowired
	private EntityManager em;

	@Test
	void 품목마다_처음_나온_상품_등급_가격만_저장한다() {
		int saved = marketPriceService.save(TUESDAY, List.of(
			new KamisPrice("사과", "후지", "중품", "10개", 25000, null),
			new KamisPrice("사과", "후지", "상품", "10개", 30000, 29000),
			new KamisPrice("사과", "홍로", "상품", "10개", 32000, null),
			// 오늘 가격이 없는 줄은 건너뛰고 다음 품종을 쓴다
			new KamisPrice("배", "신고", "상품", "10개", null, 40000),
			new KamisPrice("배", "원황", "상품", "10개", 38000, null)));

		assertThat(saved).isEqualTo(2);
		MarketPrice apple = marketPriceService.findByItemNames(List.of("사과")).get("사과");
		assertThat(apple.getKindName()).isEqualTo("후지");
		assertThat(apple.getPrice()).isEqualTo(30000);
		assertThat(apple.getWeekAgoPrice()).isEqualTo(29000);
		assertThat(apple.getSurveyDate()).isEqualTo(TUESDAY);
		assertThat(marketPriceService.findByItemNames(List.of("배")).get("배").getKindName()).isEqualTo("원황");
	}

	@Test
	void 다시_받으면_새로_만들지_않고_덮어쓴다() {
		marketPriceService.save(MONDAY, List.of(new KamisPrice("양파", "양파", "상품", "1kg", 3600, null)));
		marketPriceService.save(TUESDAY, List.of(new KamisPrice("양파", "양파", "상품", "1kg", 3450, 3600)));
		em.flush();
		em.clear();

		List<MarketPrice> all = marketPriceRepository.findAll();
		assertThat(all).hasSize(1);
		assertThat(all.get(0).getPrice()).isEqualTo(3450);
		assertThat(all.get(0).getSurveyDate()).isEqualTo(TUESDAY);
	}

	@Test
	void 받은_가격이_없으면_전날_가격을_그대로_둔다() {
		marketPriceService.save(MONDAY, List.of(new KamisPrice("양파", "양파", "상품", "1kg", 3600, null)));

		assertThat(marketPriceService.save(TUESDAY, List.of())).isZero();
		assertThat(marketPriceService.findByItemNames(List.of("양파")).get("양파").getSurveyDate()).isEqualTo(MONDAY);
	}

	@Test
	void 이름이_없으면_빈_결과를_준다() {
		assertThat(marketPriceService.findByItemNames(List.of())).isEmpty();
	}

}
