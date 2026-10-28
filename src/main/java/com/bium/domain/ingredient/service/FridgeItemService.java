package com.bium.domain.ingredient.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.dto.FridgeItemBulkCreateRequest;
import com.bium.domain.ingredient.dto.FridgeItemBulkCreateResponse;
import com.bium.domain.ingredient.dto.FridgeItemCreateRequest;
import com.bium.domain.ingredient.dto.FridgeContentsResponse;
import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.ingredient.dto.FridgeItemUpdateRequest;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class FridgeItemService {

	private final FridgeItemRepository fridgeItemRepository;
	private final IngredientCatalogRepository ingredientCatalogRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final ActivityLogService activityLogService;
	private final Clock clock;
	private final EntityManager entityManager;

	// 도감 재료를 고른 보관 위치의 중앙 칸 맨 뒤에 넣는다. 수량을 받는 화면이 없어 1로 둔다
	public FridgeItemResponse create(Long userId, Long fridgeId, FridgeItemCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		// 다른 사용자의 도감 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
		IngredientCatalog catalog = ingredientCatalogRepository.findByIdAndOwnerId(request.catalogId(), userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));

		LocalDate today = LocalDate.now(clock);
		Integer lastSortOrder = fridgeItemRepository.findMaxSortOrder(fridgeId, request.storage(), SectionType.CENTER);
		FridgeItem item = FridgeItem.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.catalog(catalog)
			.name(request.name().strip())
			.emoji(blankToNull(request.emoji()))
			.quantity(BigDecimal.ONE)
			.storage(request.storage())
			.section(SectionType.CENTER)
			.sortOrder(lastSortOrder == null ? 0 : lastSortOrder + 1)
			.purchasedAt(today)
			.expiresAt(catalog.calculateExpiresAt(today))
			.purchaseCycleDays(catalog.getPurchaseCycleDays())
			.createdBy(userRepository.getReferenceById(userId))
			.build();
		fridgeItemRepository.save(item);
		activityLogService.recordItem(userId, item, ActivityAction.ADD);
		return FridgeItemResponse.from(item);
	}

	// 영수증에서 읽은 재료처럼 도감에 없는 재료를 한 번에 넣는다. 보관 위치마다 중앙 칸 맨 뒤에 보낸 순서대로 놓는다
	public FridgeItemBulkCreateResponse createAll(Long userId, Long fridgeId, FridgeItemBulkCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);

		LocalDate today = LocalDate.now(clock);
		Fridge fridge = fridgeRepository.getReferenceById(fridgeId);
		User createdBy = userRepository.getReferenceById(userId);
		Map<StorageType, Integer> nextSortOrders = new EnumMap<>(StorageType.class);
		List<FridgeItem> items = new ArrayList<>();
		for (FridgeItemBulkCreateRequest.Item entry : request.items()) {
			int sortOrder = nextSortOrders.computeIfAbsent(entry.storage(), storage -> {
				Integer lastSortOrder = fridgeItemRepository.findMaxSortOrder(fridgeId, storage, SectionType.CENTER);
				return lastSortOrder == null ? 0 : lastSortOrder + 1;
			});
			nextSortOrders.put(entry.storage(), sortOrder + 1);
			items.add(FridgeItem.builder()
				.fridge(fridge)
				.name(entry.name().strip())
				.emoji(blankToNull(entry.emoji()))
				.quantity(BigDecimal.ONE)
				.storage(entry.storage())
				.section(SectionType.CENTER)
				.sortOrder(sortOrder)
				.purchasedAt(today)
				.expiresAt(entry.expiresAt())
				.createdBy(createdBy)
				.build());
		}
		fridgeItemRepository.saveAll(items);
		return new FridgeItemBulkCreateResponse(items.stream().map(FridgeItemResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public FridgeContentsResponse getContents(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		return FridgeContentsResponse.from(
			fridgeItemRepository.findAllByFridgeIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(fridgeId));
	}

	public FridgeItemResponse update(Long userId, Long itemId, FridgeItemUpdateRequest request) {
		FridgeItem item = findAccessibleItem(userId, itemId);
		validateVersion(item, request.version());
		// 바뀐 값이 없어 UPDATE가 안 나가도 저장할 때 버전을 한 번 더 확인한다
		entityManager.lock(item, LockModeType.OPTIMISTIC);
		item.update(request.name().strip(), blankToNull(request.emoji()), request.expiresAt());
		// 올라간 버전을 응답에 담으려고 바로 저장한다
		fridgeItemRepository.flush();
		// 바꾼 값 없이 저장만 누르면 버전이 그대로이므로 활동 기록에 쌓지 않는다
		if (!item.getVersion().equals(request.version())) {
			activityLogService.recordItem(userId, item, ActivityAction.UPDATE);
		}
		return FridgeItemResponse.from(item);
	}

	// 기록을 남기기 위해 지우지 않고 삭제 시각만 적는다
	public void delete(Long userId, Long itemId, Integer version) {
		FridgeItem item = findAccessibleItem(userId, itemId);
		validateVersion(item, version);
		item.delete(LocalDateTime.now(clock));
		// 활동 기록이 재료 행을 참조해서, 기록을 먼저 넣으면 MySQL에서 동시에 지우는 두 요청이 서로 기다리다 막힌다.
		// 삭제를 먼저 저장해 재료 행을 잡아 두면 늦은 요청은 버전 충돌(409)로 끝난다
		fridgeItemRepository.flush();
		activityLogService.recordItem(userId, item, ActivityAction.DELETE);
	}

	// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
	private FridgeItem findAccessibleItem(Long userId, Long itemId) {
		return fridgeItemRepository.findByIdAndDeletedAtIsNull(itemId)
			.filter(item -> fridgeAccessValidator.isMember(item.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
	}

	// 화면이 불러온 뒤 다른 멤버가 고쳤으면 덮어쓰지 않게 막는다
	private void validateVersion(FridgeItem item, Integer version) {
		if (!item.getVersion().equals(version)) {
			throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
