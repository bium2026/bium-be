package com.bium.domain.shopping.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.dto.FridgeItemResponse;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.ingredient.service.FridgeItemArranger;
import com.bium.domain.shopping.dto.PurchasedItemCreateRequest;
import com.bium.domain.shopping.dto.PurchasedItemListResponse;
import com.bium.domain.shopping.dto.PurchasedItemMoveRequest;
import com.bium.domain.shopping.dto.PurchasedItemResponse;
import com.bium.domain.shopping.entity.PurchaseSource;
import com.bium.domain.shopping.entity.PurchasedItem;
import com.bium.domain.shopping.repository.PurchasedItemRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchasedItemService {

	private final PurchasedItemRepository purchasedItemRepository;
	private final IngredientCatalogRepository ingredientCatalogRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final FridgeItemRepository fridgeItemRepository;
	private final FridgeItemArranger fridgeItemArranger;
	private final ActivityLogService activityLogService;
	private final Clock clock;

	// 도감 재료를 오늘 산 것으로 담는다. 같은 재료를 두 번 사는 경우도 있어 이미 담긴 재료여도 막지 않는다
	public PurchasedItemResponse create(Long userId, Long fridgeId, PurchasedItemCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		// 다른 사용자의 도감 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
		IngredientCatalog catalog = ingredientCatalogRepository.findByIdAndOwnerId(request.catalogId(), userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));

		LocalDate today = LocalDate.now(clock);
		PurchasedItem item = PurchasedItem.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.user(userRepository.getReferenceById(userId))
			.catalog(catalog)
			.name(catalog.getName())
			.emoji(catalog.getEmoji())
			.storage(catalog.getDefaultStorage())
			.purchasedAt(today)
			.expiresAt(catalog.calculateExpiresAt(today))
			.source(PurchaseSource.MANUAL)
			.build();
		purchasedItemRepository.save(item);
		return PurchasedItemResponse.from(item);
	}

	// 냉장고 멤버가 함께 쓰는 목록이라 누가 담았는지와 상관없이 최근에 담은 순서로 준다
	@Transactional(readOnly = true)
	public PurchasedItemListResponse getItems(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		List<PurchasedItemResponse> items = purchasedItemRepository
			.findAllByFridgeIdAndMovedAtIsNullOrderByIdDesc(fridgeId)
			.stream()
			.map(PurchasedItemResponse::from)
			.toList();
		return new PurchasedItemListResponse(items);
	}

	// 끌어다 놓은 냉장고 칸의 원하는 위치에 같은 이름·아이콘·유통기한으로 재료를 만든다
	public FridgeItemResponse moveToFridge(Long userId, Long purchasedItemId, PurchasedItemMoveRequest request) {
		// 장본 음식을 잠근 다음 냉장고를 잠가야, 그 뒤 칸 재료를 읽을 때 앞선 요청이 바꾼 순번까지 본다
		PurchasedItem purchasedItem = purchasedItemRepository.findByIdForUpdate(purchasedItemId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PURCHASED_ITEM_NOT_FOUND));
		Fridge fridge = fridgeRepository.findByIdForUpdate(purchasedItem.getFridge().getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		// 다른 냉장고의 장본 음식은 있는지도 알 수 없게 없는 장본 음식과 같은 오류를 준다
		if (!fridgeAccessValidator.isMember(fridge.getId(), userId)) {
			throw new BusinessException(ErrorCode.PURCHASED_ITEM_NOT_FOUND);
		}

		IngredientCatalog catalog = purchasedItem.getCatalog();
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.catalog(catalog)
			.name(purchasedItem.getName())
			.emoji(purchasedItem.getEmoji())
			// 장본 음식도 수량을 받는 화면이 없어 비어 있으면 1로 둔다
			.quantity(purchasedItem.getQuantity() == null ? BigDecimal.ONE : purchasedItem.getQuantity())
			.unit(purchasedItem.getUnit())
			.storage(request.storage())
			.section(request.section())
			.sortOrder(0)
			.purchasedAt(purchasedItem.getPurchasedAt())
			.expiresAt(purchasedItem.getExpiresAt())
			.purchaseCycleDays(catalog == null ? null : catalog.getPurchaseCycleDays())
			.createdBy(userRepository.getReferenceById(userId))
			.build();
		fridgeItemArranger.place(item, request.storage(), request.section(), request.index());
		fridgeItemRepository.save(item);
		purchasedItem.moveTo(item, LocalDateTime.now(clock));
		activityLogService.recordItem(userId, item, ActivityAction.ADD);
		return FridgeItemResponse.from(item);
	}

	// 잘못 담은 재료를 뺀다. 냉장고로 옮긴 장본 음식은 기록이라 뺄 수 없다
	public void delete(Long userId, Long purchasedItemId) {
		// 다른 냉장고의 장본 음식은 있는지도 알 수 없게 없는 장본 음식과 같은 오류를 준다
		PurchasedItem item = purchasedItemRepository.findByIdAndMovedAtIsNull(purchasedItemId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.PURCHASED_ITEM_NOT_FOUND));
		purchasedItemRepository.delete(item);
	}

}
