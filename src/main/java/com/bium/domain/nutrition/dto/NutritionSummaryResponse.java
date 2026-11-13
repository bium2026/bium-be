package com.bium.domain.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.nutrition.entity.FoodNutrition;

/**
 * 냉장고(또는 한 구역)의 영양 균형. 재료 양을 모르므로 합계에 들어간 재료마다 100g씩 있다고 보고 더한다.
 * itemCount - matchedCount - pendingCount는 맞는 식품이 없거나 값이 일부 비어서 합계에서 뺀 재료 수다.
 *
 * @param matchedCount 합계에 들어간 재료 수. 열량·탄수화물·단백질·지방이 모두 있는 재료만 센다
 * @param pendingCount 아직 식약처에서 찾아보지 않은 재료 수
 * @param caloriesKcal 합계는 matchedCount가 0이면 null이다
 * @param items 합계에 들어간 재료를 재료 id 순서로 준다
 */
public record NutritionSummaryResponse(
	int itemCount,
	int matchedCount,
	int pendingCount,
	BigDecimal caloriesKcal,
	BigDecimal carbohydrateG,
	BigDecimal proteinG,
	BigDecimal fatG,
	List<Item> items
) {

	// 값은 재료 하나 조회 API와 같이 baseAmountG(보통 100g)당 값이다
	public record Item(
		Long itemId,
		String name,
		String emoji,
		String foodName,
		BigDecimal baseAmountG,
		BigDecimal caloriesKcal,
		BigDecimal carbohydrateG,
		BigDecimal proteinG,
		BigDecimal fatG
	) {

		public static Item of(FridgeItem item, FoodNutrition nutrition) {
			return new Item(item.getId(), item.getName(), item.getEmoji(), nutrition.getFoodName(),
				nutrition.getBaseAmountG(), nutrition.getCaloriesKcal(), nutrition.getCarbohydrateG(),
				nutrition.getProteinG(), nutrition.getFatG());
		}

	}

}
