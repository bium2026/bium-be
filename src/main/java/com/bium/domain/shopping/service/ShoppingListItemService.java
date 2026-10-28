package com.bium.domain.shopping.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.shopping.dto.ShoppingListItemCheckRequest;
import com.bium.domain.shopping.dto.ShoppingListItemCreateRequest;
import com.bium.domain.shopping.dto.ShoppingListItemListResponse;
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

	// 사용자가 직접 담는 것이라 같은 이름이 이미 담겨 있어도 막지 않는다
	public ShoppingListItemResponse create(Long userId, Long fridgeId, ShoppingListItemCreateRequest request) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		ShoppingListItem item = ShoppingListItem.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.createdBy(userRepository.getReferenceById(userId))
			.name(request.name())
			.build();
		shoppingListItemRepository.save(item);
		return ShoppingListItemResponse.from(item);
	}

	// 냉장고 멤버가 함께 쓰는 목록이라 누가 담았는지와 상관없이 같은 목록을 준다
	@Transactional(readOnly = true)
	public ShoppingListItemListResponse getItems(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		List<ShoppingListItemResponse> items = shoppingListItemRepository.findAllWithRecipeByFridgeId(fridgeId)
			.stream()
			.map(ShoppingListItemResponse::from)
			.toList();
		return new ShoppingListItemListResponse(items);
	}

	public ShoppingListItemResponse check(Long userId, Long itemId, ShoppingListItemCheckRequest request) {
		ShoppingListItem item = findAccessibleItem(userId, itemId);
		item.check(request.checked());
		return ShoppingListItemResponse.from(item);
	}

	public void delete(Long userId, Long itemId) {
		shoppingListItemRepository.delete(findAccessibleItem(userId, itemId));
	}

	// 다른 냉장고의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
	private ShoppingListItem findAccessibleItem(Long userId, Long itemId) {
		return shoppingListItemRepository.findById(itemId)
			.filter(item -> fridgeAccessValidator.isMember(item.getFridge().getId(), userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.SHOPPING_LIST_ITEM_NOT_FOUND));
	}

}
