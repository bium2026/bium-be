package com.bium.domain.shopping.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.shopping.dto.PurchasedItemCreateRequest;
import com.bium.domain.shopping.dto.PurchasedItemListResponse;
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

	// 잘못 담은 재료를 뺀다. 냉장고로 옮긴 장본 음식은 기록이라 뺄 수 없다
	public void delete(Long userId, Long purchasedItemId) {
		// 다른 냉장고의 장본 음식은 있는지도 알 수 없게 없는 장본 음식과 같은 오류를 준다
		PurchasedItem item = purchasedItemRepository.findByIdAndMovedAtIsNull(purchasedItemId)
			.filter(found -> fridgeAccessValidator.isMember(found.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.PURCHASED_ITEM_NOT_FOUND));
		purchasedItemRepository.delete(item);
	}

}
