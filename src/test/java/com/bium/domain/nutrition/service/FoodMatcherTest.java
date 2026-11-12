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

	// 아래 이름들은 2026-11-11 식약처 API(FoodNtrCpntDbInfo03, 품목대표)에서 실제로 받은 이름이다
	@Test
	void 원재료_이름은_쉼표_뒤_세부_이름까지_보고_생것을_먼저_고른다() {
		Optional<Food> match = FoodMatcher.match("양파", List.of(
			food("D1", "양파볶음", "음식"),
			food("R2", "양파, 말린것", "원재료성"),
			food("R3", "양파, 찐것", "원재료성"),
			food("R4", "양파, 생것", "원재료성"),
			food("R5", "양파, 적색, 생것", "원재료성"),
			food("D6", "양파 장아찌", "음식")));

		assertThat(match).map(Food::foodCode).hasValue("R4");
	}

	@Test
	void 쉼표로_나눈_이름_중_하나가_재료_이름이면_고른다() {
		assertThat(FoodMatcher.match("대파", List.of(food("R1", "파, 대파, 생것", "원재료성"))))
			.map(Food::foodCode).hasValue("R1");
		assertThat(FoodMatcher.match("배추김치", List.of(food("R1", "김치, 배추 김치", "원재료성"))))
			.map(Food::foodCode).hasValue("R1");
	}

	@Test
	void 세부_이름이_앞_이름을_품지_않으면_고르지_않는다() {
		// 엿, 쌀은 쌀로 만든 엿이다
		assertThat(FoodMatcher.match("쌀", List.of(food("R1", "엿, 쌀", "원재료성")))).isEmpty();
	}

	@Test
	void 재료_이름으로_시작하는_식품을_다른_이름_뒤에_재료_이름이_있는_식품보다_먼저_고른다() {
		Optional<Food> match = FoodMatcher.match("감자", List.of(
			food("R1", "수제비면, 감자, 생것", "원재료성"),
			food("R2", "감자, 수미, 생것", "원재료성"),
			food("D3", "감자_찐감자", "음식")));

		assertThat(match).map(Food::foodCode).hasValue("R2");
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
