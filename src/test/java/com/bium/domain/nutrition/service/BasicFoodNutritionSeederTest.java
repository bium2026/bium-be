package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;

import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.domain.nutrition.service.BasicFoodNutritionSeeder.BasicFood;
import com.bium.global.nutrition.FoodNutritionClient.Food;

class BasicFoodNutritionSeederTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), SEOUL);

	private final FoodNutritionRepository foodNutritionRepository = mock(FoodNutritionRepository.class);
	private final BasicFoodNutritionSeeder seeder = new BasicFoodNutritionSeeder(foodNutritionRepository, CLOCK);

	@Test
	void 파일의_재료는_검색어가_겹치지_않고_네_영양_값이_모두_있다() {
		List<BasicFood> foods = seeder.readBasicFoods();

		assertThat(foods).hasSize(76);
		assertThat(foods).extracting(BasicFood::searchName).doesNotHaveDuplicates()
			.allSatisfy(name -> assertThat(name).isNotBlank().doesNotContainAnyWhitespaces().hasSizeLessThanOrEqualTo(50));
		assertThat(foods).allSatisfy(food -> {
			assertThat(food.foodCode()).isNotBlank().hasSizeLessThanOrEqualTo(30);
			assertThat(food.foodName()).isNotBlank().hasSizeLessThanOrEqualTo(100);
			assertThat(food.baseAmountG()).isPositive();
			assertThat(food.toFood().hasAllValues()).as(food.searchName()).isTrue();
		});
	}

	@Test
	void 자동_선택이_틀리는_재료는_직접_고른_식품을_쓴다() {
		Map<String, String> foodNames = seeder.readBasicFoods().stream()
			.collect(Collectors.toMap(BasicFood::searchName, BasicFood::foodName));

		assertThat(foodNames)
			.containsEntry("닭가슴살", "닭고기, 가슴, 생것")
			.containsEntry("새송이버섯", "큰느타리버섯(새송이버섯), 생것")
			.containsEntry("삼겹살", "돼지고기, 삼겹살, 생것")
			// 검색어 별칭(계란 → 달걀)으로 찾으므로 달걀로 넣는다
			.containsEntry("달걀", "달걀, 생것");
	}

	@Test
	void 없는_검색어는_파일_값으로_넣는다() {
		given(foodNutritionRepository.findAllBySearchNameIn(anyCollection())).willReturn(List.of());

		int saved = seeder.seed(List.of(basicFood("두부", "P106-001000100-0001")));

		assertThat(saved).isEqualTo(1);
		FoodNutrition nutrition = savedNutritions().get(0);
		assertThat(nutrition.getSearchName()).isEqualTo("두부");
		assertThat(nutrition.getFoodCode()).isEqualTo("P106-001000100-0001");
		assertThat(nutrition.getCaloriesKcal()).isEqualByComparingTo("97.00");
		assertThat(nutrition.getFetchedAt()).isEqualTo(LocalDateTime.now(CLOCK));
	}

	@Test
	void 같은_식품으로_저장된_검색어는_건너뛰고_못_찾음이나_다른_식품은_바꾼다() {
		FoodNutrition same = saved("두부", "P106-001000100-0001");
		FoodNutrition notFound = saved("삼겹살", null);
		FoodNutrition other = saved("닭가슴살", "OTHER-CODE");
		given(foodNutritionRepository.findAllBySearchNameIn(anyCollection())).willReturn(List.of(same, notFound, other));

		int saved = seeder.seed(List.of(
			basicFood("두부", "P106-001000100-0001"),
			basicFood("삼겹살", "R109-014006601-0000"),
			basicFood("닭가슴살", "R109-008000401-0000")));

		assertThat(saved).isEqualTo(2);
		Map<String, FoodNutrition> changed = savedNutritions().stream()
			.collect(Collectors.toMap(FoodNutrition::getSearchName, Function.identity()));
		assertThat(changed).containsOnlyKeys("삼겹살", "닭가슴살");
		assertThat(changed.get("삼겹살").getFoodCode()).isEqualTo("R109-014006601-0000");
		assertThat(changed.get("닭가슴살").getFoodCode()).isEqualTo("R109-008000401-0000");
	}

	@Test
	void 저장하다_실패해도_서버는_켜진다() {
		given(foodNutritionRepository.findAllBySearchNameIn(anyCollection())).willReturn(List.of());
		willThrow(new DataIntegrityViolationException("duplicate")).given(foodNutritionRepository).saveAll(any());

		seeder.run(new DefaultApplicationArguments());

		verify(foodNutritionRepository).saveAll(any());
	}

	@SuppressWarnings("unchecked")
	private List<FoodNutrition> savedNutritions() {
		ArgumentCaptor<List<FoodNutrition>> captor = ArgumentCaptor.forClass(List.class);
		verify(foodNutritionRepository).saveAll(captor.capture());
		return captor.getValue();
	}

	private static BasicFood basicFood(String searchName, String foodCode) {
		return new BasicFood(searchName, foodCode, searchName, new BigDecimal("100.00"), new BigDecimal("97.00"),
			new BigDecimal("3.75"), new BigDecimal("9.62"), new BigDecimal("4.63"));
	}

	private static FoodNutrition saved(String searchName, String foodCode) {
		FoodNutrition nutrition = new FoodNutrition(searchName);
		Food food = foodCode == null ? null : new Food(foodCode, searchName, null, new BigDecimal("100.00"),
			new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00"));
		nutrition.update(food, LocalDateTime.now(CLOCK).minusDays(1));
		return nutrition;
	}

}
