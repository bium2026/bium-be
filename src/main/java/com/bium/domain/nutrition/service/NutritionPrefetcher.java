package com.bium.domain.nutrition.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 영양 균형 요약에서 아직 찾아보지 않은 재료 이름을 백그라운드에서 식약처로 찾아 저장해 둔다.
 * 화면은 잠시 뒤 요약을 다시 불러 결과를 받는다. 식약처 호출 한도를 아끼려고 적은 스레드로 차례대로 부르고,
 * 이미 찾고 있는 이름은 다시 넣지 않는다.
 */
@Slf4j
@Component
public class NutritionPrefetcher {

	private static final int THREADS = 2;
	// 요약 요청 하나가 작업 하나를 넣는다. 넘치면 버리고 다음 요약 요청 때 다시 넣는다
	private static final int QUEUE_CAPACITY = 100;

	private final FoodNutritionLookup foodNutritionLookup;
	private final Executor executor;
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	@Autowired
	public NutritionPrefetcher(FoodNutritionLookup foodNutritionLookup) {
		this(foodNutritionLookup, createExecutor());
	}

	NutritionPrefetcher(FoodNutritionLookup foodNutritionLookup, Executor executor) {
		this.foodNutritionLookup = foodNutritionLookup;
		this.executor = executor;
	}

	public void prefetch(Collection<String> searchNames) {
		List<String> names = searchNames.stream()
			.distinct()
			.filter(inFlight::add)
			.toList();
		if (names.isEmpty()) {
			return;
		}
		try {
			executor.execute(() -> fetchAll(names));
		} catch (RejectedExecutionException exception) {
			names.forEach(inFlight::remove);
			log.warn("영양 정보 미리 찾기 대기열이 가득 차서 이름 {}개를 이번에는 넘깁니다.", names.size());
		}
	}

	private void fetchAll(List<String> names) {
		try {
			for (String name : names) {
				foodNutritionLookup.findOrFetch(name);
			}
		} catch (RuntimeException exception) {
			// 식약처가 실패하면 남은 이름도 실패하기 쉬워서 멈춘다. 실패한 이름은 저장하지 않았으므로 다음 요약 요청 때 다시 찾는다
			log.warn("영양 정보를 미리 찾다가 멈췄습니다. error={}", exception.getMessage());
		} finally {
			names.forEach(inFlight::remove);
		}
	}

	@PreDestroy
	void shutdown() {
		if (executor instanceof ExecutorService service) {
			service.shutdownNow();
		}
	}

	private static ExecutorService createExecutor() {
		CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("nutrition-prefetch-");
		threadFactory.setDaemon(true);
		return new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory);
	}

}
