package com.bium.domain.ingredient.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.ingredient.dto.IngredientCatalogCreateRequest;
import com.bium.domain.ingredient.dto.IngredientCatalogListResponse;
import com.bium.domain.ingredient.dto.IngredientCatalogResponse;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 사용자마다 따로 갖는 식재료 도감(owner_id가 본인인 ingredient_catalog)을 다룬다.
 * 공용 도감(owner_id NULL)은 보여 주지도 지우지도 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientCatalogService {

	private final IngredientCatalogRepository ingredientCatalogRepository;
	private final UserRepository userRepository;

	// 방금 추가한 재료가 앞에 오도록 최근에 추가한 순서로 준다
	public IngredientCatalogListResponse getMyCatalog(Long userId) {
		List<IngredientCatalogResponse> ingredients = ingredientCatalogRepository.findAllByOwnerIdOrderByIdDesc(userId)
			.stream()
			.map(IngredientCatalogResponse::from)
			.toList();
		return new IngredientCatalogListResponse(ingredients);
	}

	@Transactional
	public IngredientCatalogResponse create(Long userId, IngredientCatalogCreateRequest request) {
		String name = request.name().strip();
		if (ingredientCatalogRepository.existsByOwnerIdAndName(userId, name)) {
			throw new BusinessException(ErrorCode.DUPLICATE_CATALOG_NAME);
		}

		IngredientCatalog catalog = IngredientCatalog.builder()
			.owner(userRepository.getReferenceById(userId))
			.name(name)
			.emoji(request.emoji().strip())
			.category(request.category())
			.shelfLifeDays(request.shelfLifeDays())
			.build();
		try {
			ingredientCatalogRepository.save(catalog);
		} catch (DataIntegrityViolationException e) {
			// 같은 이름을 동시에 추가하면 위 확인을 둘 다 통과하고 (owner_key, name) 유니크 제약에서 걸린다
			throw new BusinessException(ErrorCode.DUPLICATE_CATALOG_NAME);
		}
		return IngredientCatalogResponse.from(catalog);
	}

	@Transactional
	public void delete(Long userId, Long catalogId) {
		// 다른 사용자의 재료나 공용 도감은 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
		IngredientCatalog catalog = ingredientCatalogRepository.findByIdAndOwnerId(catalogId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));

		// 이 재료를 가리키는 행이 남아 있으면 외래 키 때문에 지워지지 않는다
		ingredientCatalogRepository.detachFridgeItems(catalogId);
		ingredientCatalogRepository.detachPurchasedItems(catalogId);
		ingredientCatalogRepository.detachShoppingListItems(catalogId);
		ingredientCatalogRepository.detachRecipeIngredients(catalogId);
		ingredientCatalogRepository.deleteNutritionInfo(catalogId);
		ingredientCatalogRepository.delete(catalog);
	}

}
