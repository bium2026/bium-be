package com.bium.domain.nutrition.dto;

import java.math.BigDecimal;

import com.bium.domain.nutrition.entity.FoodNutrition;

/**
 * 재료 하나의 영양 정보. 값은 baseAmountG(보통 100g)당 값이고, 식약처에 없는 값은 null이다.
 */
public record NutritionResponse(
	String foodName,
	BigDecimal baseAmountG,
	BigDecimal caloriesKcal,
	BigDecimal carbohydrateG,
	BigDecimal proteinG,
	BigDecimal fatG
) {

	public static NutritionResponse from(FoodNutrition nutrition) {
		return new NutritionResponse(nutrition.getFoodName(), nutrition.getBaseAmountG(), nutrition.getCaloriesKcal(),
			nutrition.getCarbohydrateG(), nutrition.getProteinG(), nutrition.getFatG());
	}

}
