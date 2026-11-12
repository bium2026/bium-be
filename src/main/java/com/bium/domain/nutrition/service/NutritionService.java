package com.bium.domain.nutrition.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
import com.bium.global.nutrition.FoodNutritionClient.SearchPage;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고 재료의 영양 정보를 식약처 식품영양성분 DB에서 찾아 준다. 찾은 결과는 검색어마다 저장해 두고 다시 부르지 않는다.
 * 식약처 응답을 기다리는 동안 DB 연결을 잡아 두지 않도록 트랜잭션을 걸지 않고, 읽기와 저장을 따로 한다.
 */
@Service
@RequiredArgsConstructor
public class NutritionService {

	private static final Duration NOT_FOUND_RETRY_INTERVAL = Duration.ofDays(30);
	// 한 번 찾을 때 식약처를 부르는 최대 횟수. 마지막 페이지는 4~6초 걸리고 재료 수정 모달이 기다리는 시간이라 적게 둔다
	private static final int MAX_SEARCH_PAGES = 2;
	// 첫 페이지가 이보다 오래 걸렸으면 다음 페이지는 받지 않는다. 한 번 부르는 데 최대 12초(연결 2초 + 응답 10초)라서
	// 모달이 기다리는 시간을 많아야 약 17초로 묶는다
	private static final Duration MORE_PAGES_BUDGET = Duration.ofSeconds(5);
	private static final Pattern SPACES = Pattern.compile("\\s+");
	// 식약처가 다른 이름을 쓰는 흔한 재료. 검색어를 식약처 이름으로 바꿔 찾는다
	private static final Map<String, String> SEARCH_ALIASES = Map.of("계란", "달걀");

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

	/**
	 * 도감 재료는 냉장고에서 이름을 고쳐도 같은 식품을 보여 주도록 도감 이름으로 찾는다.
	 * 식약처 식품 이름에는 띄어쓰기가 거의 없고 검색은 띄어쓰기까지 맞아야 해서, 띄어쓰기를 지운 이름으로 찾고 저장한다.
	 */
	private String searchName(FridgeItem item) {
		String name = item.getCatalog() == null ? item.getName() : item.getCatalog().getName();
		String compact = SPACES.matcher(name).replaceAll("");
		return SEARCH_ALIASES.getOrDefault(compact, compact);
	}

	private FoodNutrition findOrFetch(String searchName) {
		LocalDateTime now = LocalDateTime.now(clock);
		Optional<FoodNutrition> saved = foodNutritionRepository.findBySearchName(searchName);
		if (saved.isPresent() && !saved.get().needsRefetch(now, NOT_FOUND_RETRY_INTERVAL)) {
			return saved.get();
		}

		Food food;
		try {
			food = searchBestMatch(searchName);
		} catch (BusinessException exception) {
			// 호출이 실패하면 아무것도 저장하지 않고 다음 요청 때 다시 찾는다.
			// 30일이 지나 다시 찾던 "못 찾음" 결과가 있으면 오류 대신 그 결과를 그대로 준다
			if (saved.isPresent()) {
				return saved.get();
			}
			throw exception;
		}
		FoodNutrition nutrition = saved.orElseGet(() -> new FoodNutrition(searchName));
		nutrition.update(food, now);
		try {
			return foodNutritionRepository.save(nutrition);
		} catch (DataIntegrityViolationException exception) {
			// 같은 이름을 동시에 처음 찾으면 둘 다 저장하다 유니크 제약에 걸린다. 먼저 저장된 결과를 쓴다
			return foodNutritionRepository.findBySearchName(searchName).orElseThrow(() -> exception);
		}
	}

	/**
	 * 검색은 이름 일부만 맞아도 오고 순서가 없어서, 이름이 같은 식품이 첫 페이지에 없을 수 있다.
	 * 이름이 같은 식품이 나오거나 결과가 끝날 때까지 몇 페이지 더 받아 본 뒤 그 안에서 고른다.
	 * 시간이 모자라 다음 페이지를 받지 못했으면 받은 결과 안에서 고른다. 실패로 두면 같은 이름을 찾을 때마다 다시 시간이 모자라 끝내 저장되지 않는다.
	 */
	private Food searchBestMatch(String searchName) {
		long startedAt = clock.millis();
		List<Food> foods = new ArrayList<>();
		for (int pageNo = 1; pageNo <= MAX_SEARCH_PAGES; pageNo++) {
			SearchPage page = foodNutritionClient.searchByName(searchName, pageNo);
			foods.addAll(page.foods());
			boolean outOfTime = clock.millis() - startedAt > MORE_PAGES_BUDGET.toMillis();
			if (!page.hasNext() || FoodMatcher.hasExactMatch(searchName, page.foods()) || outOfTime) {
				break;
			}
		}
		return FoodMatcher.match(searchName, foods).orElse(null);
	}

}
