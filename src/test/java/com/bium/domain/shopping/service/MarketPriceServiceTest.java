package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
	void 품목마다_품종과_등급을_통틀어_가장_싼_가격을_저장한다() {
		int saved = marketPriceService.save(TUESDAY, List.of(
			new KamisPrice("사과", "후지", "상품", "10개", 30000, 29000),
			new KamisPrice("사과", "후지", "중품", "10개", 25000, 26000),
			new KamisPrice("사과", "홍로", "상품", "10개", 32000, null),
			new KamisPrice("사과", "홍로", "중품", "10개", 24000, 23000)));

		assertThat(saved).isEqualTo(1);
		MarketPrice apple = marketPriceService.findByItemNames(List.of("사과")).get("사과");
		assertThat(apple.getKindName()).isEqualTo("홍로");
		assertThat(apple.getGrade()).isEqualTo("중품");
		assertThat(apple.getUnit()).isEqualTo("10개");
		assertThat(apple.getPrice()).isEqualTo(24000);
		assertThat(apple.getWeekAgoPrice()).isEqualTo(23000);
		assertThat(apple.getSurveyDate()).isEqualTo(TUESDAY);
	}

	@Test
	void 단위가_다른_가격은_더_싸도_비교하지_않는다() {
		marketPriceService.save(TUESDAY, List.of(
			new KamisPrice("쌀", "일반계", "상품", "20kg", 55000, null),
			new KamisPrice("쌀", "일반계", "중품", "20kg", 52000, null),
			new KamisPrice("쌀", "찹쌀", "상품", "1kg", 4500, null)));

		MarketPrice rice = marketPriceService.findByItemNames(List.of("쌀")).get("쌀");
		assertThat(rice.getUnit()).isEqualTo("20kg");
		assertThat(rice.getPrice()).isEqualTo(52000);
	}

	@Test
	void 오늘_가격이_없는_줄은_건너뛴다() {
		marketPriceService.save(TUESDAY, List.of(
			new KamisPrice("배", "신고", "중품", "10개", null, 30000),
			new KamisPrice("배", "신고", "상품", "10개", 38000, null)));

		MarketPrice pear = marketPriceService.findByItemNames(List.of("배")).get("배");
		assertThat(pear.getGrade()).isEqualTo("상품");
		assertThat(pear.getPrice()).isEqualTo(38000);
	}

	@Test
	void 다시_받으면_새로_만들지_않고_덮어쓴다() {
		marketPriceService.save(MONDAY, List.of(new KamisPrice("양파", "양파", "상품", "1kg", 3600, null)));
		marketPriceService.save(TUESDAY, List.of(new KamisPrice("양파", "양파", "중품", "1kg", 3150, 3300)));
		em.flush();
		em.clear();

		List<MarketPrice> all = marketPriceRepository.findAll();
		assertThat(all).hasSize(1);
		assertThat(all.get(0).getPrice()).isEqualTo(3150);
		assertThat(all.get(0).getGrade()).isEqualTo("중품");
		assertThat(all.get(0).getSurveyDate()).isEqualTo(TUESDAY);
	}

	@Test
	void 받은_가격이_없으면_전날_가격을_그대로_둔다() {
		marketPriceService.save(MONDAY, List.of(new KamisPrice("양파", "양파", "상품", "1kg", 3600, null)));

		assertThat(marketPriceService.save(TUESDAY, List.of())).isZero();
		assertThat(marketPriceService.findByItemNames(List.of("양파")).get("양파").getSurveyDate()).isEqualTo(MONDAY);
	}

	@Test
	void 흔히_쓰는_이름은_KAMIS_품목명으로_찾고_넘겨받은_이름을_키로_준다() {
		marketPriceService.save(TUESDAY, List.of(
			new KamisPrice("파", "대파(1kg)", "상품", "1kg", 2900, null),
			new KamisPrice("돼지", "앞다리", "앞다리", "100g", 1538, null),
			new KamisPrice("양파", "양파(1kg)", "상품", "1kg", 1946, null)));

		Map<String, MarketPrice> prices = marketPriceService.findByItemNames(
			List.of("대파", "파", "돼지고기", "양파", "삼겹살", "두부"));

		assertThat(prices).containsOnlyKeys("대파", "파", "돼지고기", "양파");
		assertThat(prices.get("대파").getItemName()).isEqualTo("파");
		assertThat(prices.get("대파")).isSameAs(prices.get("파"));
		assertThat(prices.get("돼지고기").getPrice()).isEqualTo(1538);
	}

	@Test
	void 이름이_없으면_빈_결과를_준다() {
		assertThat(marketPriceService.findByItemNames(List.of())).isEmpty();
	}

}
