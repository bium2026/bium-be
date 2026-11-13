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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bium.domain.ingredient.entity.FridgeItem;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 재료 영양 정보를 백그라운드에서 식약처로 찾아 저장해 둔다. 식약처는 한 번 찾는 데 4~6초 걸려서,
 * 재료를 넣을 때와 영양 균형 요약에서 아직 찾아보지 않은 재료가 있을 때 미리 찾는다.
 * 이름마다 작업을 하나씩 넣어 스레드 둘이 함께 찾고, 대기 중이거나 찾는 중인 이름은 다시 넣지 않는다.
 * 식약처 호출 한도를 아끼려고 스레드는 둘만 쓴다.
 */
@Slf4j
@Component
public class NutritionPrefetcher {

	private static final int THREADS = 2;
	// 이름 하나가 작업 하나다. 넘치면 버리고 다음 요약 요청 때 다시 넣는다
	private static final int QUEUE_CAPACITY = 200;

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

	/**
	 * 재료를 넣는 트랜잭션 안에서 부른다. 도감 이름을 읽어야 해서 검색어는 지금 정하고, 커밋된 뒤에만 찾기 시작한다.
	 * 넣기가 되돌려지면 찾지 않는다. 응답은 기다리지 않는다.
	 */
	public void prefetchAfterCommit(Collection<FridgeItem> items) {
		List<String> searchNames = items.stream()
			.map(FoodNutritionLookup::searchName)
			.distinct()
			.toList();
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			prefetch(searchNames);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				prefetch(searchNames);
			}
		});
	}

	public void prefetch(Collection<String> searchNames) {
		// 키가 없으면 모두 실패하므로 작업을 넣지 않는다. 요약을 다시 부를 때마다 실패 로그가 쌓이지 않게 한다
		if (!foodNutritionLookup.canSearch()) {
			return;
		}
		int rejected = 0;
		for (String searchName : searchNames) {
			if (!inFlight.add(searchName)) {
				continue;
			}
			try {
				executor.execute(() -> fetch(searchName));
			} catch (RejectedExecutionException exception) {
				inFlight.remove(searchName);
				rejected++;
			}
		}
		if (rejected > 0) {
			log.warn("영양 정보 미리 찾기 대기열이 가득 차서 이름 {}개를 이번에는 넘깁니다.", rejected);
		}
	}

	// 실패하면 저장하지 않았으므로 다음 요약 요청 때 다시 넣는다
	private void fetch(String searchName) {
		try {
			foodNutritionLookup.findOrFetch(searchName);
		} catch (RuntimeException exception) {
			log.warn("영양 정보를 미리 찾지 못했습니다. searchName={}, error={}", searchName, exception.getMessage());
		} finally {
			inFlight.remove(searchName);
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
