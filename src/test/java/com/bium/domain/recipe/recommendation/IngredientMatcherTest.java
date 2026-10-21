package com.bium.domain.recipe.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IngredientMatcherTest {

	@Test
	void 이름이_겹치면_같은_재료로_본다() {
		IngredientMatcher matcher = new IngredientMatcher(List.of("돼지고기 앞다리살", "두부"));

		assertThat(matcher.isAvailable("돼지고기")).isTrue();
		assertThat(matcher.isAvailable("순두부")).isTrue();
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

}
