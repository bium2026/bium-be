package com.bium.domain.recipe.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.bium.global.exception.TooManyRequestsException;

class RecommendationRateLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
	private final RecommendationRateLimiter rateLimiter = new RecommendationRateLimiter(2, 3, clock);

	@Test
	void 한도를_넘으면_남은_시간과_함께_거부한다() {
		rateLimiter.check("1.1.1.1");
		clock.advance(Duration.ofSeconds(20));
		rateLimiter.check("1.1.1.1");

		assertThatThrownBy(() -> rateLimiter.check("1.1.1.1"))
			.isInstanceOfSatisfying(TooManyRequestsException.class,
				exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(40));
	}

	@Test
	void 클라이언트마다_따로_센다() {
		rateLimiter.check("1.1.1.1");
		rateLimiter.check("1.1.1.1");

		assertThatCode(() -> rateLimiter.check("2.2.2.2")).doesNotThrowAnyException();
	}

	@Test
	void 일분이_지나면_다시_허용한다() {
		rateLimiter.check("1.1.1.1");
		rateLimiter.check("1.1.1.1");
		clock.advance(Duration.ofMinutes(1));

		assertThatCode(() -> rateLimiter.check("1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void 서버_전체_한도를_넘으면_거부한다() {
		rateLimiter.check("1.1.1.1");
		rateLimiter.check("2.2.2.2");
		rateLimiter.check("3.3.3.3");

		assertThatThrownBy(() -> rateLimiter.check("4.4.4.4"))
			.isInstanceOf(TooManyRequestsException.class);
	}

	private static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

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
