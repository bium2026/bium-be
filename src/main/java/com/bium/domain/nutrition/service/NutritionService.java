package com.bium.domain.nutrition.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.nutrition.dto.NutritionResponse;
import com.bium.domain.nutrition.dto.NutritionSummaryResponse;
import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고 재료의 영양 정보를 식약처 식품영양성분 DB에서 찾아 준다. 찾은 결과는 검색어마다 저장해 두고 다시 부르지 않는다.
 * 식약처 응답을 기다리는 동안 DB 연결을 잡아 두지 않도록 트랜잭션을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NutritionService {

	private final FridgeItemRepository fridgeItemRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final FoodNutritionLookup foodNutritionLookup;
	private final NutritionPrefetcher nutritionPrefetcher;

	// 맞는 식품이 없거나 영양 값이 하나도 없으면 null을 준다
	public NutritionResponse getItemNutrition(Long userId, Long itemId) {
		// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
		FridgeItem item = fridgeItemRepository.findWithCatalogById(itemId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
		FoodNutrition nutrition = foodNutritionLookup.findOrFetch(FoodNutritionLookup.searchName(item));
		return nutrition.hasAnyValue() ? NutritionResponse.from(nutrition) : null;
	}

	/**
	 * 냉장고 재료의 영양 정보를 100g씩 있다고 보고 더한다. storage가 null이면 냉장고 전체, 아니면 그 구역만 더한다.
	 * 이 요청에서는 식약처를 기다리지 않고 저장된 결과만 쓴다. 아직 찾아보지 않은 재료는 pendingCount로 알려 주고 백그라운드에서 찾는다.
	 * 영양소마다 더한 재료가 달라지면 비율이 틀어지므로 네 값이 모두 있는 재료만 더한다.
	 */
	public NutritionSummaryResponse getFridgeSummary(Long userId, Long fridgeId, StorageType storage) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		List<FridgeItem> items = fridgeItemRepository.findAllWithCatalogByFridgeId(fridgeId, storage);
		Map<String, FoodNutrition> saved = foodNutritionLookup.findSaved(items.stream()
			.map(FoodNutritionLookup::searchName)
			.collect(Collectors.toSet()));

		List<NutritionSummaryResponse.Item> matched = new ArrayList<>();
		List<FoodNutrition> matchedNutritions = new ArrayList<>();
		Set<String> pendingNames = new LinkedHashSet<>();
		int pendingCount = 0;
		for (FridgeItem item : items) {
			String searchName = FoodNutritionLookup.searchName(item);
			FoodNutrition nutrition = saved.get(searchName);
			if (nutrition == null) {
				pendingCount++;
				pendingNames.add(searchName);
			} else if (nutrition.hasAllValues()) {
				matched.add(NutritionSummaryResponse.Item.of(item, nutrition));
				matchedNutritions.add(nutrition);
			}
		}
		if (!pendingNames.isEmpty()) {
			nutritionPrefetcher.prefetch(pendingNames);
		}

		return new NutritionSummaryResponse(items.size(), matched.size(), pendingCount,
			sumPer100g(matchedNutritions, FoodNutrition::getCaloriesKcal),
			sumPer100g(matchedNutritions, FoodNutrition::getCarbohydrateG),
			sumPer100g(matchedNutritions, FoodNutrition::getProteinG),
			sumPer100g(matchedNutritions, FoodNutrition::getFatG),
			matched);
	}

	// 같은 재료가 두 줄이면 두 번 더한다. 더한 재료가 없으면 null
	private BigDecimal sumPer100g(List<FoodNutrition> nutritions, Function<FoodNutrition, BigDecimal> value) {
		return nutritions.stream()
			.map(nutrition -> nutrition.per100g(value.apply(nutrition)))
			.reduce(BigDecimal::add)
			.orElse(null);
	}

}
