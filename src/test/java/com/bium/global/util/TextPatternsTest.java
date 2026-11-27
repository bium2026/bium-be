package com.bium.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextPatternsTest {

	@Test
	void 이모지_한_개는_국기_피부색_ZWJ로_이은_이모지까지_받는다() {
		assertThat(new String[] {"🥕", "🧅", "🫚", "❤️", "☕", "🍽️", "🇰🇷", "👍🏽", "🧑‍🍳", "👨‍👩‍👧‍👦", " 🥕 "})
			.allSatisfy(emoji -> assertThat(emoji).matches(TextPatterns.SINGLE_EMOJI));
	}

	@Test
	void 빈_값은_받고_필수인지는_쓰는_쪽에서_정한다() {
		assertThat("").matches(TextPatterns.SINGLE_EMOJI);
		assertThat("  ").matches(TextPatterns.SINGLE_EMOJI);
	}

	@Test
	void 글자나_여러_이모지나_도형_문자는_받지_않는다() {
		assertThat(new String[] {"a", "가", "두부", "1", "1️⃣", "■", "🥕🥕", "a🥕", "🥕a", "🥕 🥕", "\n🥕", "🥕 "})
			.allSatisfy(emoji -> assertThat(emoji).doesNotMatch(TextPatterns.SINGLE_EMOJI));
	}

}
