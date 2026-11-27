package com.bium.domain.recipe.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IngredientMatcherTest {

	@Test
	void 이름이_겹치면_같은_재료로_본다() {
		IngredientMatcher matcher = new IngredientMatcher(List.of("돼지고기 앞다리살", "두부"));

		assertThat(matcher.isAvailable("돼지고기")).isTrue();
		assertThat(matcher.isAvailable("두부 한 모")).isTrue();
	}

	@Test
	void 한_글자_이름은_다른_재료에_포함돼도_같은_재료로_보지_않는다() {
		IngredientMatcher matcher = new IngredientMatcher(List.of("파", "김", "배"));

		assertThat(matcher.isAvailable("양파")).isFalse();
		assertThat(matcher.isAvailable("김치")).isFalse();
		assertThat(matcher.isAvailable("배추")).isFalse();
		assertThat(IngredientMatcher.matches("양파", "파")).isFalse();
	}

	@Test
	void 한_글자_이름도_완전히_같으면_같은_재료로_본다() {
		IngredientMatcher matcher = new IngredientMatcher(List.of("파"));

		assertThat(matcher.isAvailable("파")).isTrue();
		assertThat(IngredientMatcher.matches("파", "파")).isTrue();
	}

	@Test
	void 이름이_겹쳐도_다른_재료는_같은_재료로_보지_않는다() {
		assertThat(IngredientMatcher.matches("배추", "양배추")).isFalse();
		assertThat(IngredientMatcher.matches("양배추 1/2통", "배추")).isFalse();
		assertThat(IngredientMatcher.matches("배추", "배추김치")).isFalse();
		assertThat(IngredientMatcher.matches("고추", "고추장")).isFalse();
		assertThat(IngredientMatcher.matches("버터", "땅콩버터")).isFalse();
		assertThat(IngredientMatcher.matches("새우", "새우젓")).isFalse();
		assertThat(IngredientMatcher.matches("순두부", "두부")).isFalse();
		assertThat(IngredientMatcher.matches("두부 한 모", "순두부")).isFalse();
		assertThat(IngredientMatcher.matches("오이", "오이지")).isFalse();
		assertThat(new IngredientMatcher(List.of("양배추", "배추김치")).isAvailable("배추")).isFalse();
	}

	@Test
	void 구분하는_쌍이라도_같은_이름이거나_같은_재료를_담으면_같은_재료로_본다() {
		assertThat(IngredientMatcher.matches("양배추", "양배추")).isTrue();
		assertThat(IngredientMatcher.matches("양배추", "양배추 1/2통")).isTrue();
		assertThat(IngredientMatcher.matches("토마토", "방울토마토")).isTrue();
	}

	@Test
	void 계란과_달걀은_같은_재료로_본다() {
		assertThat(IngredientMatcher.matches("계란", "달걀")).isTrue();
		assertThat(new IngredientMatcher(List.of("달걀")).isAvailable("계란 2개")).isTrue();
		assertThat(new IngredientMatcher(List.of("계란말이")).isAvailable("달걀")).isTrue();
	}

	@Test
	void 한쪽_이름이_다른_쪽을_포함하면_여전히_같은_재료로_본다() {
		assertThat(IngredientMatcher.matches("돼지고기 앞다리살", "돼지고기")).isTrue();
		assertThat(IngredientMatcher.matches("두부 한 모", "두부")).isTrue();
	}

}
