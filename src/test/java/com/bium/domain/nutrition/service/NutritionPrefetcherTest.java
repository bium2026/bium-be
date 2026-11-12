package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

class NutritionPrefetcherTest {

	private final FoodNutritionLookup lookup = mock(FoodNutritionLookup.class);
	private final QueuedExecutor executor = new QueuedExecutor();
	private final NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, executor);

	@BeforeEach
	void setUp() {
		given(lookup.canSearch()).willReturn(true);
	}

	@Test
	void 이름마다_작업을_하나씩_넣어_스레드들이_함께_찾게_한다() {
		prefetcher.prefetch(List.of("두부", "김치", "두부"));

		assertThat(executor.tasks).hasSize(2);
		executor.runAll();
		verify(lookup).findOrFetch("두부");
		verify(lookup).findOrFetch("김치");
	}

	@Test
	void 대기_중이거나_찾는_중인_이름은_다시_넣지_않고_끝나면_다시_넣을_수_있다() {
		prefetcher.prefetch(List.of("두부"));
		prefetcher.prefetch(List.of("두부", "김치"));
		assertThat(executor.tasks).hasSize(2);

		executor.runAll();
		prefetcher.prefetch(List.of("두부"));
		assertThat(executor.tasks).hasSize(1);
	}

	@Test
	void 한_이름이_실패해도_다른_이름은_찾고_실패한_이름은_다음에_다시_넣는다() {
		given(lookup.findOrFetch("두부")).willThrow(new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED));

		prefetcher.prefetch(List.of("두부", "김치"));
		executor.runAll();
		verify(lookup).findOrFetch("김치");

		prefetcher.prefetch(List.of("두부"));
		executor.runAll();
		verify(lookup, times(2)).findOrFetch("두부");
	}

	@Test
	void 대기열이_가득_차면_넘기고_다음에_다시_넣을_수_있다() {
		executor.rejecting = true;
		prefetcher.prefetch(List.of("두부"));

		executor.rejecting = false;
		prefetcher.prefetch(List.of("두부"));

		assertThat(executor.tasks).hasSize(1);
	}

	@Test
	void 식약처_키가_없으면_작업을_넣지_않는다() {
		given(lookup.canSearch()).willReturn(false);

		prefetcher.prefetch(List.of("두부"));

		assertThat(executor.tasks).isEmpty();
	}

	@Test
	void 재료를_넣는_트랜잭션이_커밋된_뒤에만_찾기_시작한다() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			prefetcher.prefetchAfterCommit(List.of(item("두부"), item("방울 토마토"), item("두부")));
			assertThat(executor.tasks).isEmpty();

			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		assertThat(executor.tasks).hasSize(2);
		executor.runAll();
		verify(lookup).findOrFetch("두부");
		verify(lookup).findOrFetch("방울토마토");
	}

	@Test
	void 재료를_넣는_트랜잭션이_되돌려지면_찾지_않는다() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			prefetcher.prefetchAfterCommit(List.of(item("두부")));

			TransactionSynchronizationManager.getSynchronizations()
				.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		assertThat(executor.tasks).isEmpty();
	}

	private static FridgeItem item(String name) {
		return FridgeItem.builder().name(name).quantity(BigDecimal.ONE).build();
	}

	private static class QueuedExecutor implements Executor {

		private final List<Runnable> tasks = new ArrayList<>();
		private boolean rejecting;

		@Override
		public void execute(Runnable task) {
			if (rejecting) {
				throw new RejectedExecutionException("가득 참");
			}
			tasks.add(task);
		}

		void runAll() {
			List<Runnable> queued = new ArrayList<>(tasks);
			tasks.clear();
			queued.forEach(Runnable::run);
		}

	}

}
