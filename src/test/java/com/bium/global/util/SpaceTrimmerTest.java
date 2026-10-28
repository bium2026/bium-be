package com.bium.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SpaceTrimmerTest {

	@Test
	void 앞뒤의_일반_공백과_전각_공백을_뺀다() {
		assertThat(SpaceTrimmer.trim("  두부 　 ")).isEqualTo("두부");
		assertThat(SpaceTrimmer.trim("두부  조림")).isEqualTo("두부  조림");
		assertThat(SpaceTrimmer.trim("　 ")).isEmpty();
		assertThat(SpaceTrimmer.trim(null)).isNull();
	}

	@Test
	void 줄바꿈과_탭은_남겨서_검증에서_막게_한다() {
		assertThat(SpaceTrimmer.trim(" \n두부\t ")).isEqualTo("\n두부\t");
	}

	@Test
	void 공백이_길게_이어져도_오래_걸리지_않는다() {
		String value = "a" + " ".repeat(200_000) + "b";

		String trimmed = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> SpaceTrimmer.trim(value));

		assertThat(trimmed).isEqualTo(value);
	}

}
