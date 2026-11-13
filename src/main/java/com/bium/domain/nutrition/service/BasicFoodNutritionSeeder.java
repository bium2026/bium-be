package com.bium.domain.nutrition.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.global.nutrition.FoodNutritionClient.Food;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자주 쓰는 재료의 식약처 영양 정보(resources/nutrition/basic-foods.tsv)를 서버를 켤 때 food_nutrition에 넣어 둔다.
 * 처음 보는 재료도 식약처 조회(4~6초)를 기다리지 않게 하고, 자동 선택(FoodMatcher)이 대표값이 아닌 부위를 고르거나
 * 결과를 찾지 못하는 재료는 직접 고른 식품으로 보여 준다. 저장된 값이 있으면 식약처를 부르지 않으므로 파일 값이 먼저 쓰인다.
 * 식약처는 부르지 않는다. 없는 검색어, 못 찾음으로 저장된 검색어, 다른 식품으로 저장된 검색어만 파일 값으로 바꿔서
 * 서버를 다시 켜도 같은 값을 또 쓰지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nutrition-api", name = "seed-basic-foods", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BasicFoodNutritionSeeder implements ApplicationRunner {

	static final String RESOURCE = "nutrition/basic-foods.tsv";
	private static final int COLUMNS = 8;

	private final FoodNutritionRepository foodNutritionRepository;
	private final Clock clock;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int saved = seed(readBasicFoods());
			if (saved > 0) {
				log.info("자주 쓰는 재료 영양 정보 {}개를 food_nutrition에 넣었습니다.", saved);
			}
		} catch (DataAccessException | UncheckedIOException | IllegalArgumentException | IllegalStateException exception) {
			// 미리 넣기는 조회를 빠르게 하려는 것이라, 실패해도 서버는 켜고 재료는 지금처럼 식약처에서 찾게 둔다
			log.error("자주 쓰는 재료 영양 정보를 미리 넣지 못했습니다.", exception);
		}
	}

	// 바꾼 검색어 수를 돌려준다. saveAll 한 번으로 저장해서, 중간에 실패하면 하나도 바뀌지 않는다
	int seed(List<BasicFood> foods) {
		Map<String, FoodNutrition> saved = foodNutritionRepository.findAllBySearchNameIn(
				foods.stream().map(BasicFood::searchName).toList()).stream()
			.collect(Collectors.toMap(FoodNutrition::getSearchName, Function.identity(), (first, second) -> first));
		LocalDateTime now = LocalDateTime.now(clock);
		List<FoodNutrition> changed = new ArrayList<>();
		for (BasicFood food : foods) {
			FoodNutrition nutrition = saved.get(food.searchName());
			if (nutrition != null && food.foodCode().equals(nutrition.getFoodCode())) {
				continue;
			}
			FoodNutrition target = nutrition == null ? new FoodNutrition(food.searchName()) : nutrition;
			target.update(food.toFood(), now);
			changed.add(target);
		}
		foodNutritionRepository.saveAll(changed);
		return changed.size();
	}

	List<BasicFood> readBasicFoods() {
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines()
				.filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("search_name\t"))
				.map(BasicFoodNutritionSeeder::parse)
				.toList();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	// 식품 이름에 쉼표가 들어가서("양파, 생것") 탭으로 나눈다
	private static BasicFood parse(String line) {
		String[] columns = line.split("\t", -1);
		if (columns.length != COLUMNS) {
			throw new IllegalStateException(RESOURCE + "의 칸 수가 " + COLUMNS + "개가 아닙니다: " + line);
		}
		return new BasicFood(columns[0], columns[1], columns[2], amount(columns[3]), amount(columns[4]),
			amount(columns[5]), amount(columns[6]), amount(columns[7]));
	}

	private static BigDecimal amount(String value) {
		return value.isBlank() ? null : new BigDecimal(value);
	}

	record BasicFood(
		String searchName,
		String foodCode,
		String foodName,
		BigDecimal baseAmountG,
		BigDecimal caloriesKcal,
		BigDecimal carbohydrateG,
		BigDecimal proteinG,
		BigDecimal fatG
	) {

		Food toFood() {
			return new Food(foodCode, foodName, null, baseAmountG, caloriesKcal, carbohydrateG, proteinG, fatG);
		}

	}

}
