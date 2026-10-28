package com.bium.domain.recipe.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.recipe.dto.CookingCandidatesResponse;
import com.bium.domain.recipe.dto.CookingCandidatesResponse.IngredientCandidates;
import com.bium.domain.recipe.dto.CookingLogResponse;
import com.bium.domain.recipe.dto.CookingRequest;
import com.bium.domain.recipe.dto.CookingUndoResponse;
import com.bium.domain.recipe.entity.CookingLog;
import com.bium.domain.recipe.entity.CookingLogItem;
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.recommendation.IngredientMatcher;
import com.bium.domain.recipe.repository.CookingLogItemRepository;
import com.bium.domain.recipe.repository.CookingLogRepository;
import com.bium.domain.recipe.repository.RecipeIngredientRepository;
import com.bium.domain.recipe.repository.RecipeRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 요리 완료 후 냉장고 재료 차감과 되돌리기를 맡는다.
 * 쓴 재료는 수량과 상관없이 모두 빼며, 무엇을 뺄지는 서버가 추천하고 사용자가 화면에서 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CookingService {

	// 유통기한이 빠른 재료부터 먼저 쓰도록 추천한다. 유통기한이 없으면 뒤로 보낸다
	private static final Comparator<FridgeItem> CANDIDATE_ORDER = Comparator
		.comparing(FridgeItem::getExpiresAt, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
		.thenComparing(FridgeItem::getId);

	private final RecipeRepository recipeRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final FridgeItemRepository fridgeItemRepository;
	private final CookingLogRepository cookingLogRepository;
	private final CookingLogItemRepository cookingLogItemRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final ActivityLogService activityLogService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public CookingCandidatesResponse getCandidates(Long userId, Long recipeId) {
		Recipe recipe = findAccessibleRecipe(userId, recipeId);
		List<FridgeItem> fridgeItems = fridgeItemRepository.findAllByFridgeIdAndDeletedAtIsNull(
			recipe.getFridge().getId());

		// 소금·간장 같은 기본 양념은 냉장고에서 관리하지 않으므로 뺄 후보를 찾지 않는다
		List<IngredientCandidates> ingredients = recipeIngredientRepository.findAllByRecipeIdInOrderByIdAsc(
				List.of(recipeId)).stream()
			.filter(ingredient -> ingredient.getSource() != IngredientSource.BASIC)
			.map(ingredient -> new IngredientCandidates(ingredient.getName(), ingredient.getAmount(),
				fridgeItems.stream()
					.filter(item -> IngredientMatcher.matches(ingredient.getName(), item.getName()))
					.sorted(CANDIDATE_ORDER)
					.map(FridgeItemResponse::from)
					.toList()))
			.toList();
		return new CookingCandidatesResponse(recipeId, ingredients);
	}

	// 재료를 하나라도 뺄 수 없으면 아무것도 빼지 않는다
	public CookingLogResponse cook(Long userId, Long recipeId, CookingRequest request) {
		Recipe recipe = findAccessibleRecipe(userId, recipeId);
		Long fridgeId = recipe.getFridge().getId();

		// 레시피 재료 두 개가 같은 냉장고 재료로 추천될 수 있어 같은 재료는 한 번만 뺀다
		Map<Long, Integer> versions = new LinkedHashMap<>();
		request.items().forEach(item -> versions.putIfAbsent(item.fridgeItemId(), item.version()));

		Map<Long, FridgeItem> items = fridgeItemRepository.findAllById(versions.keySet()).stream()
			.collect(Collectors.toMap(FridgeItem::getId, Function.identity()));
		for (Map.Entry<Long, Integer> entry : versions.entrySet()) {
			FridgeItem item = items.get(entry.getKey());
			// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
			if (item == null || item.isDeleted() || !item.getFridge().getId().equals(fridgeId)) {
				throw new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND);
			}
			if (!item.getVersion().equals(entry.getValue())) {
				throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
			}
		}

		CookingLog cookingLog = cookingLogRepository.save(
			new CookingLog(recipe.getFridge(), recipe, userRepository.getReferenceById(userId)));
		LocalDateTime now = LocalDateTime.now(clock);
		List<CookingLogItem> logItems = new ArrayList<>();
		for (Long itemId : versions.keySet()) {
			FridgeItem item = items.get(itemId);
			BigDecimal quantityBefore = item.getQuantity();
			logItems.add(CookingLogItem.builder()
				.cookingLog(cookingLog)
				.fridgeItem(item)
				.quantityBefore(quantityBefore)
				.deductedQuantity(item.useUp(now))
				.deletedByDeduction(true)
				.build());
		}
		cookingLogItemRepository.saveAll(logItems);
		// 그 사이 다른 멤버가 같은 재료를 고쳤으면 여기서 버전 충돌로 막는다
		fridgeItemRepository.flush();
		// 활동 기록이 재료 행을 참조하므로 차감을 먼저 저장해 재료 행을 잡아 둔 뒤 기록을 남긴다
		versions.keySet().forEach(itemId ->
			activityLogService.recordItem(userId, items.get(itemId), ActivityAction.DEDUCT));

		return new CookingLogResponse(cookingLog.getId(), recipeId, List.copyOf(versions.keySet()),
			cookingLog.getCookedAt());
	}

	public CookingUndoResponse undo(Long userId, Long cookingLogId) {
		CookingLog cookingLog = cookingLogRepository.findByIdForUpdate(cookingLogId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.COOKING_LOG_NOT_FOUND));
		if (cookingLog.isUndone()) {
			throw new BusinessException(ErrorCode.ALREADY_UNDONE);
		}

		List<FridgeItem> restoredItems = new ArrayList<>();
		for (CookingLogItem logItem : cookingLogItemRepository.findAllWithFridgeItemByCookingLogId(cookingLogId)) {
			logItem.getFridgeItem().restore(logItem.getDeductedQuantity(), logItem.isDeletedByDeduction());
			restoredItems.add(logItem.getFridgeItem());
		}
		LocalDateTime now = LocalDateTime.now(clock);
		cookingLog.undo(now);
		fridgeItemRepository.flush();
		restoredItems.forEach(item -> activityLogService.recordItem(userId, item, ActivityAction.RESTORE));

		return new CookingUndoResponse(cookingLogId, restoredItems.stream().map(FridgeItem::getId).toList(), now);
	}

	// 다른 냉장고의 레시피는 있는지도 알 수 없게 없는 레시피와 같은 오류를 준다
	private Recipe findAccessibleRecipe(Long userId, Long recipeId) {
		return recipeRepository.findById(recipeId)
			.filter(recipe -> fridgeAccessValidator.isMember(recipe.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
	}

}
