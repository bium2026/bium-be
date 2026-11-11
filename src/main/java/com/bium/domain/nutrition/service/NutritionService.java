package com.bium.domain.nutrition.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.nutrition.dto.NutritionResponse;
import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.nutrition.FoodNutritionClient;
import com.bium.global.nutrition.FoodNutritionClient.Food;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고 재료의 영양 정보를 식약처 식품영양성분 DB에서 찾아 준다. 찾은 결과는 검색어마다 저장해 두고 다시 부르지 않는다.
 * 식약처 응답을 기다리는 동안 DB 연결을 잡아 두지 않도록 트랜잭션을 걸지 않고, 읽기와 저장을 따로 한다.
 */
@Service
@RequiredArgsConstructor
public class NutritionService {

	private static final Duration NOT_FOUND_RETRY_INTERVAL = Duration.ofDays(30);

	private final FridgeItemRepository fridgeItemRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final FoodNutritionRepository foodNutritionRepository;
	private final FoodNutritionClient foodNutritionClient;
	private final Clock clock;

	// 맞는 식품이 없거나 영양 값이 하나도 없으면 null을 준다
	public NutritionResponse getItemNutrition(Long userId, Long itemId) {
		// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
		FridgeItem item = fridgeItemRepository.findWithCatalogById(itemId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
		FoodNutrition nutrition = findOrFetch(searchName(item));
		return nutrition.hasAnyValue() ? NutritionResponse.from(nutrition) : null;
	}

	// 도감 재료는 냉장고에서 이름을 고쳐도 같은 식품을 보여 주도록 도감 이름으로 찾는다
	private String searchName(FridgeItem item) {
		String name = item.getCatalog() == null ? item.getName() : item.getCatalog().getName();
		return name.strip();
	}

	private FoodNutrition findOrFetch(String searchName) {
		LocalDateTime now = LocalDateTime.now(clock);
		Optional<FoodNutrition> saved = foodNutritionRepository.findBySearchName(searchName);
		if (saved.isPresent() && !saved.get().needsRefetch(now, NOT_FOUND_RETRY_INTERVAL)) {
			return saved.get();
		}

		// 호출이 실패하면 예외가 그대로 나가서 아무것도 저장하지 않는다. 다음 요청 때 다시 찾는다
		Food food = FoodMatcher.match(searchName, foodNutritionClient.searchByName(searchName)).orElse(null);
		FoodNutrition nutrition = saved.orElseGet(() -> new FoodNutrition(searchName));
		nutrition.update(food, now);
		try {
			return foodNutritionRepository.save(nutrition);
		} catch (DataIntegrityViolationException exception) {
			// 같은 이름을 동시에 처음 찾으면 둘 다 저장하다 유니크 제약에 걸린다. 먼저 저장된 결과를 쓴다
			return foodNutritionRepository.findBySearchName(searchName).orElseThrow(() -> exception);
		}
	}

}
