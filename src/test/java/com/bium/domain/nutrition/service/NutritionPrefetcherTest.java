package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

class NutritionPrefetcherTest {

	private final FoodNutritionLookup lookup = mock(FoodNutritionLookup.class);

	@BeforeEach
	void setUp() {
		given(lookup.canSearch()).willReturn(true);
	}

	@Test
	void 넘긴_이름을_하나씩_찾는다() {
		NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, Runnable::run);

		prefetcher.prefetch(List.of("두부", "김치", "두부"));

		verify(lookup).findOrFetch("두부");
		verify(lookup).findOrFetch("김치");
	}

	@Test
	void 찾는_중인_이름은_다시_넣지_않고_끝나면_다시_넣을_수_있다() {
		QueuedExecutor executor = new QueuedExecutor();
		NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, executor);

		prefetcher.prefetch(List.of("두부"));
		prefetcher.prefetch(List.of("두부"));
		assertThat(executor.tasks).hasSize(1);

		executor.runAll();
		prefetcher.prefetch(List.of("두부"));
		assertThat(executor.tasks).hasSize(1);
		executor.runAll();

		verify(lookup, times(2)).findOrFetch("두부");
	}

	@Test
	void 식약처가_실패하면_남은_이름은_찾지_않고_다음에_다시_찾는다() {
		NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, Runnable::run);
		given(lookup.findOrFetch("두부")).willThrow(new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED));

		prefetcher.prefetch(List.of("두부", "김치"));
		verify(lookup, never()).findOrFetch("김치");

		prefetcher.prefetch(List.of("두부", "김치"));
		verify(lookup, times(2)).findOrFetch("두부");
	}

	@Test
	void 대기열이_가득_차면_넘기고_다음에_다시_넣을_수_있다() {
		QueuedExecutor executor = new QueuedExecutor();
		executor.rejecting = true;
		NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, executor);

		prefetcher.prefetch(List.of("두부"));
		executor.rejecting = false;
		prefetcher.prefetch(List.of("두부"));

		assertThat(executor.tasks).hasSize(1);
	}

	@Test
	void 식약처_키가_없으면_작업을_넣지_않는다() {
		QueuedExecutor executor = new QueuedExecutor();
		NutritionPrefetcher prefetcher = new NutritionPrefetcher(lookup, executor);
		given(lookup.canSearch()).willReturn(false);

		prefetcher.prefetch(List.of("두부"));

		assertThat(executor.tasks).isEmpty();
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
