package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bium.global.nutrition.FoodNutritionClient.Food;

class FoodMatcherTest {

	@Test
	void 이름이_같은_식품을_고른다() {
		Optional<Food> match = FoodMatcher.match("두부", List.of(
			food("D1", "두부조림", "음식"),
			food("R2", "두부_부침용", "원재료성"),
			food("R3", "두부", "원재료성")));

		assertThat(match).map(Food::foodCode).hasValue("R3");
	}

	@Test
	void 이름이_같은_식품이_없으면_세부_이름만_붙은_식품_중_짧은_것을_고른다() {
		Optional<Food> match = FoodMatcher.match("두부", List.of(
			food("R1", "두부(찌개용 순두부)", "원재료성"),
			food("R2", "두부_부침용", "원재료성"),
			food("D3", "두부조림", "음식")));

		assertThat(match).map(Food::foodCode).hasValue("R2");
	}

	@Test
	void 다른_음식만_있으면_고르지_않는다() {
		Optional<Food> match = FoodMatcher.match("두부", List.of(
			food("D1", "두부조림", "음식"),
			food("D2", "순두부찌개", "음식"),
			food("D3", "찌개_두부", "음식")));

		assertThat(match).isEmpty();
	}

	@Test
	void 이름이_같으면_원재료성_식품을_먼저_고른다() {
		Optional<Food> match = FoodMatcher.match("두부", List.of(
			food("D1", "두부", "음식"),
			food("R2", "두부", "원재료성")));

		assertThat(match).map(Food::foodCode).hasValue("R2");
	}

	@Test
	void 이름이_같으면_네_값이_모두_있는_식품을_먼저_고른다() {
		Food partial = new Food("R1", "두부", "원재료성", BigDecimal.valueOf(100), BigDecimal.valueOf(84), null,
			BigDecimal.ONE, BigDecimal.ONE);

		Optional<Food> match = FoodMatcher.match("두부", List.of(partial, food("D2", "두부", "음식")));

		assertThat(match).map(Food::foodCode).hasValue("D2");
	}

	@Test
	void 영양_값이_하나도_없는_식품은_고르지_않는다() {
		Food empty = new Food("R1", "두부", "원재료성", BigDecimal.valueOf(100), null, null, null, null);

		assertThat(FoodMatcher.match("두부", List.of(empty))).isEmpty();
	}

	@Test
	void 띄어쓰기와_영문_대소문자가_달라도_같은_이름으로_본다() {
		assertThat(FoodMatcher.match("방울 토마토", List.of(food("R1", "방울토마토", "원재료성")))).isPresent();
		assertThat(FoodMatcher.match("Spam", List.of(food("R1", "SPAM", "가공식품")))).isPresent();
	}

	private static Food food(String foodCode, String foodName, String groupName) {
		return new Food(foodCode, foodName, groupName, BigDecimal.valueOf(100), BigDecimal.valueOf(84),
			BigDecimal.valueOf(1.8), BigDecimal.valueOf(9.3), BigDecimal.valueOf(4.6));
	}

}
