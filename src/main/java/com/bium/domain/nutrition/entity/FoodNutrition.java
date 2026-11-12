package com.bium.domain.nutrition.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import com.bium.global.nutrition.FoodNutritionClient.Food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식약처 식품영양성분 DB에서 찾은 결과를 검색어(재료 이름)마다 저장해 두고 다시 부르지 않는다.
 * 도감 재료와 영수증 재료가 같은 이름이면 같은 행을 쓴다. 맞는 식품이 없었으면 식품코드와 영양 값을 비운 행으로 남긴다.
 * 컬럼 이름 끝의 단위(_g)는 기본 네이밍 전략이 언더스코어로 바꾸지 못해서 직접 지정한다
 */
@Entity
@Table(name = "food_nutrition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodNutrition {

	private static final int MAX_FOOD_NAME_LENGTH = 100;
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 식약처에 보낸 검색어. 도감 재료는 도감 이름, 나머지는 재료 이름이다
	@Column(nullable = false, unique = true, length = 50)
	private String searchName;

	// 식품영양성분 DB 식품코드. NULL이면 맞는 식품이 없었다
	@Column(length = 30)
	private String foodCode;

	// 식약처에서 맞춘 식품 이름. 재료 이름과 다를 수 있어 화면에 함께 보여 준다
	@Column(length = MAX_FOOD_NAME_LENGTH)
	private String foodName;

	@Column(name = "base_amount_g", precision = 7, scale = 2)
	private BigDecimal baseAmountG;

	@Column(name = "calories_kcal", precision = 7, scale = 2)
	private BigDecimal caloriesKcal;

	@Column(name = "carbohydrate_g", precision = 7, scale = 2)
	private BigDecimal carbohydrateG;

	@Column(name = "protein_g", precision = 7, scale = 2)
	private BigDecimal proteinG;

	@Column(name = "fat_g", precision = 7, scale = 2)
	private BigDecimal fatG;

	@Column(nullable = false)
	private LocalDateTime fetchedAt;

	public FoodNutrition(String searchName) {
		this.searchName = searchName;
	}

	// 식약처에서 찾은 결과로 바꾼다. food가 null이면 맞는 식품이 없던 것으로 남긴다
	public void update(Food food, LocalDateTime fetchedAt) {
		this.fetchedAt = fetchedAt;
		if (food == null) {
			this.foodCode = null;
			this.foodName = null;
			this.baseAmountG = null;
			this.caloriesKcal = null;
			this.carbohydrateG = null;
			this.proteinG = null;
			this.fatG = null;
			return;
		}
		this.foodCode = food.foodCode();
		this.foodName = food.foodName().length() > MAX_FOOD_NAME_LENGTH
			? food.foodName().substring(0, MAX_FOOD_NAME_LENGTH) : food.foodName();
		this.baseAmountG = food.baseAmountG();
		this.caloriesKcal = food.caloriesKcal();
		this.carbohydrateG = food.carbohydrateG();
		this.proteinG = food.proteinG();
		this.fatG = food.fatG();
	}

	public boolean isFound() {
		return foodCode != null;
	}

	public boolean hasAnyValue() {
		return caloriesKcal != null || carbohydrateG != null || proteinG != null || fatG != null;
	}

	public boolean hasAllValues() {
		return caloriesKcal != null && carbohydrateG != null && proteinG != null && fatG != null;
	}

	// 기준량(baseAmountG)당 값을 100g당 값으로 바꾼다. 기준량을 모르면 100g으로 본다
	public BigDecimal per100g(BigDecimal value) {
		if (baseAmountG == null || baseAmountG.signum() <= 0 || baseAmountG.compareTo(HUNDRED) == 0) {
			return value;
		}
		return value.multiply(HUNDRED).divide(baseAmountG, 2, RoundingMode.HALF_UP);
	}

	// 맞는 식품이 없던 이름은 식약처 DB가 바뀌었을 수 있어 일정 기간이 지나면 다시 찾는다. 찾은 식품은 다시 부르지 않는다
	public boolean needsRefetch(LocalDateTime now, Duration notFoundRetryInterval) {
		return !isFound() && fetchedAt.plus(notFoundRetryInterval).isBefore(now);
	}

}
