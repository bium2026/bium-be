package com.bium.domain.recipe.recommendation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bium.global.exception.TooManyRequestsException;

/**
 * Gemini 사용 한도를 지키기 위해 분당 추천 요청 수를 제한한다.
 * 클라이언트별 한도와 서버 전체 한도를 함께 본다.
 * 로그인 기능이 생기기 전까지 IP를 클라이언트 기준으로 쓰며, 서버 한 대의 메모리에만 기록한다.
 */
@Component
public class RecommendationRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final int MAX_TRACKED_CLIENTS = 10_000;

	private final Map<String, Window> clientWindows = new ConcurrentHashMap<>();
	private final AtomicReference<Window> globalWindow = new AtomicReference<>();
	private final AtomicReference<Instant> lastCleanupAt;
	private final int limitPerMinute;
	private final int globalLimitPerMinute;
	private final Clock clock;

	public RecommendationRateLimiter(
		@Value("${recipe.recommendation.rate-limit-per-minute}") int limitPerMinute,
		@Value("${recipe.recommendation.global-rate-limit-per-minute}") int globalLimitPerMinute,
		Clock clock) {
		this.limitPerMinute = limitPerMinute;
		this.globalLimitPerMinute = globalLimitPerMinute;
		this.clock = clock;
		this.lastCleanupAt = new AtomicReference<>(clock.instant());
	}

	public void check(String clientKey) {
		Instant now = clock.instant();
		cleanUpExpiredWindows(now);

		// 기록할 수 있는 클라이언트 수를 넘으면 새 클라이언트는 다음 정리 때까지 받지 않는다
		if (!clientWindows.containsKey(clientKey) && clientWindows.size() >= MAX_TRACKED_CLIENTS) {
			throw new TooManyRequestsException(retryAfterSeconds(lastCleanupAt.get(), now));
		}

		Window clientWindow = clientWindows.compute(clientKey, (key, current) -> next(current, now));
		if (clientWindow.count() > limitPerMinute) {
			throw new TooManyRequestsException(retryAfterSeconds(clientWindow.startedAt(), now));
		}

		Window window = globalWindow.updateAndGet(current -> next(current, now));
		if (window.count() > globalLimitPerMinute) {
			throw new TooManyRequestsException(retryAfterSeconds(window.startedAt(), now));
		}
	}

	// 정리는 1분에 한 번만 한다
	private void cleanUpExpiredWindows(Instant now) {
		Instant lastCleanup = lastCleanupAt.get();
		if (now.isBefore(lastCleanup.plus(WINDOW)) || !lastCleanupAt.compareAndSet(lastCleanup, now)) {
			return;
		}
		clientWindows.values().removeIf(window -> window.isExpired(now));
	}

	private Window next(Window current, Instant now) {
		return current == null || current.isExpired(now) ? new Window(now, 1) : current.increase();
	}

	private long retryAfterSeconds(Instant windowStartedAt, Instant now) {
		Duration remaining = Duration.between(now, windowStartedAt.plus(WINDOW));
		return Math.max(1, (remaining.toMillis() + 999) / 1000);
	}

	private record Window(Instant startedAt, int count) {

		boolean isExpired(Instant now) {
			return !now.isBefore(startedAt.plus(WINDOW));
		}

		Window increase() {
			return new Window(startedAt, count + 1);
		}

	}

}
