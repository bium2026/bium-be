package com.bium.domain.shopping.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.shopping.dto.ShoppingListItemBulkCreateRequest;
import com.bium.domain.shopping.dto.ShoppingListItemCheckRequest;
import com.bium.domain.shopping.dto.ShoppingListItemCreateRequest;
import com.bium.domain.shopping.dto.ShoppingListItemListResponse;
import com.bium.domain.shopping.dto.ShoppingListItemRenameRequest;
import com.bium.domain.shopping.dto.ShoppingListItemResponse;
import com.bium.domain.shopping.entity.ShoppingListItem;
import com.bium.domain.shopping.repository.ShoppingListItemRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ShoppingListItemService {

	private final ShoppingListItemRepository shoppingListItemRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final MarketPriceService marketPriceService;

	// 사용자가 직접 담는 것이라 같은 이름이 이미 담겨 있어도 막지 않는다
	public ShoppingListItemResponse create(Long userId, Long fridgeId, ShoppingListItemCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		ShoppingListItem item = ShoppingListItem.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.createdBy(userRepository.getReferenceById(userId))
			.name(request.name())
			.build();
		shoppingListItemRepository.save(item);
		return toResponse(item);
	}

	// 다음 구매 날짜 달력에서 고른 날의 재료처럼 여러 재료를 담는다. 새로 담은 항목만 돌려준다.
	// 아직 안 산 같은 이름(공백을 빼고 비교)이 목록에 있거나 요청 안에서 겹치면 한 번만 담는다. 이미 산 재료는 또 살 수 있어 다시 담는다
	public ShoppingListItemListResponse addAll(Long userId, Long fridgeId, ShoppingListItemBulkCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		// 같은 냉장고에 동시에 담으면 둘 다 아직 안 담긴 것으로 보고 같은 재료를 두 번 담으므로 냉장고 행을 잠가 하나씩 처리한다
		fridgeRepository.findByIdForUpdate(fridgeId);
		Set<String> taken = shoppingListItemRepository.findAllForUpdateByFridgeIdAndIsCheckedFalse(fridgeId).stream()
			.map(item -> compact(item.getName()))
			.collect(Collectors.toCollection(HashSet::new));
		List<ShoppingListItem> items = new ArrayList<>();
		for (String name : request.names()) {
			if (taken.add(compact(name))) {
				items.add(ShoppingListItem.builder()
					.fridge(fridgeRepository.getReferenceById(fridgeId))
					.createdBy(userRepository.getReferenceById(userId))
					.name(name)
					.build());
			}
		}
		shoppingListItemRepository.saveAll(items);
		return ShoppingListItemListResponse.of(items, marketPriceService.findByItemNames(
			items.stream().map(ShoppingListItem::getName).collect(Collectors.toSet())));
	}

	// 냉장고 멤버가 함께 쓰는 목록이라 누가 담았는지와 상관없이 같은 목록을 준다
	@Transactional(readOnly = true)
	public ShoppingListItemListResponse getItems(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		List<ShoppingListItem> items = shoppingListItemRepository.findAllWithRecipeByFridgeId(fridgeId);
		return ShoppingListItemListResponse.of(items, marketPriceService.findByItemNames(
			items.stream().map(ShoppingListItem::getName).collect(Collectors.toSet())));
	}

	public ShoppingListItemResponse check(Long userId, Long itemId, ShoppingListItemCheckRequest request) {
		ShoppingListItem item = findAccessibleItem(userId, itemId);
		item.check(request.checked());
		return toResponse(item);
	}

	// 담을 때처럼 같은 이름이 목록에 있어도 막지 않는다. 시세는 바꾼 이름으로 다시 찾는다
	public ShoppingListItemResponse rename(Long userId, Long itemId, ShoppingListItemRenameRequest request) {
		ShoppingListItem item = findAccessibleItem(userId, itemId);
		item.rename(request.name());
		return toResponse(item);
	}

	public void delete(Long userId, Long itemId) {
		shoppingListItemRepository.delete(findAccessibleItem(userId, itemId));
	}

	// 지우는 순간 체크돼 있는 항목을 모두 지운다. 그사이 다른 멤버가 체크를 풀었으면 살 것이라 남긴다
	public void deleteChecked(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		shoppingListItemRepository.deleteCheckedByFridgeId(fridgeId);
	}

	private ShoppingListItemResponse toResponse(ShoppingListItem item) {
		return ShoppingListItemResponse.of(item,
			marketPriceService.findByItemNames(List.of(item.getName())).get(item.getName()));
	}

	private static String compact(String name) {
		return name.replaceAll("\\s", "");
	}

	// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
	private ShoppingListItem findAccessibleItem(Long userId, Long itemId) {
		return shoppingListItemRepository.findById(itemId)
			.filter(item -> fridgeAccessValidator.isMember(item.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.SHOPPING_LIST_ITEM_NOT_FOUND));
	}

}
