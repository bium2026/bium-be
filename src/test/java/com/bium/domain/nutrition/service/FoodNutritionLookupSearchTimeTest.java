package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.global.nutrition.FoodNutritionClient;
import com.bium.global.nutrition.FoodNutritionClient.Food;
import com.bium.global.nutrition.FoodNutritionClient.SearchPage;

/**
 * 첫 페이지가 오래 걸리면 다음 페이지를 받지 않아 재료 수정 모달이 너무 오래 기다리지 않는지 확인한다.
 * 시간을 직접 움직이려고 스프링 없이 만든다.
 */
class FoodNutritionLookupSearchTimeTest {

	private final FoodNutritionRepository foodNutritionRepository = mock(FoodNutritionRepository.class);
	private final FoodNutritionClient foodNutritionClient = mock(FoodNutritionClient.class);
	private final MutableClock clock = new MutableClock();
	private final FoodNutritionLookup lookup = new FoodNutritionLookup(foodNutritionRepository, foodNutritionClient, clock);

	@BeforeEach
	void setUp() {
		given(foodNutritionRepository.findBySearchName("두부")).willReturn(Optional.empty());
		given(foodNutritionRepository.save(any(FoodNutrition.class))).willAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void 첫_페이지가_5초를_넘게_걸리면_다음_페이지를_받지_않고_받은_결과에서_고른다() {
		given(foodNutritionClient.searchByName("두부", 1)).willAnswer(invocation -> {
			clock.advance(Duration.ofMillis(5_001));
			return new SearchPage(List.of(food("두부_부침용")), true);
		});

		assertThat(lookup.findOrFetch("두부").getFoodName()).isEqualTo("두부_부침용");
		verify(foodNutritionClient, never()).searchByName("두부", 2);
	}

	@Test
	void 첫_페이지가_빨리_오면_다음_페이지까지_받는다() {
		given(foodNutritionClient.searchByName("두부", 1)).willAnswer(invocation -> {
			clock.advance(Duration.ofSeconds(2));
			return new SearchPage(List.of(food("두부_부침용")), true);
		});
		given(foodNutritionClient.searchByName("두부", 2)).willReturn(new SearchPage(List.of(food("두부")), false));

		assertThat(lookup.findOrFetch("두부").getFoodName()).isEqualTo("두부");
	}

	private static Food food(String name) {
		return new Food("F-" + name, name, "원재료성", BigDecimal.valueOf(100), BigDecimal.valueOf(84),
			BigDecimal.valueOf(1.8), BigDecimal.valueOf(9.3), BigDecimal.valueOf(4.6));
	}

	private static class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-11T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
