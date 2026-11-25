package com.bium.domain.ingredient.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bium.domain.ingredient.dto.IngredientCatalogCreateRequest;
import com.bium.domain.ingredient.dto.IngredientCatalogListResponse;
import com.bium.domain.ingredient.dto.IngredientCatalogResponse;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientFavorite;
import com.bium.domain.ingredient.entity.IngredientFavoriteId;
import com.bium.domain.ingredient.entity.IngredientImage;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.ingredient.repository.IngredientFavoriteRepository;
import com.bium.domain.ingredient.repository.IngredientImageRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 식재료 도감을 다룬다. 사용자가 직접 추가한 재료(owner_id가 본인)와 공용 도감의 기본 재료(owner_id NULL)를 함께 보여 준다.
 * 기본 재료는 즐겨찾기와 냉장고에 넣기는 되지만, 사용자가 지우거나 사진을 바꿀 수 없다.
 * 내가 추가한 재료에는 이모지 대신 사진을 올릴 수 있고, 그 재료로 넣은 냉장고 재료·장본 음식도 같은 사진을 보여 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientCatalogService {

	private final IngredientCatalogRepository ingredientCatalogRepository;
	private final IngredientFavoriteRepository ingredientFavoriteRepository;
	private final IngredientImageRepository ingredientImageRepository;
	private final IngredientImageProcessor ingredientImageProcessor;
	private final UserRepository userRepository;

	// 즐겨찾기한 재료를 먼저 준다. 그 안에서는 내가 추가한 재료를 최근 순서로, 그다음 기본 재료를 넣은 순서로 준다.
	// 기본 재료를 넣기 전에 같은 이름을 직접 추가한 사용자도 있어서, 내 재료와 이름이 같은 기본 재료는 빼고 보여 준다
	public IngredientCatalogListResponse getMyCatalog(Long userId) {
		Set<Long> favoriteIds = ingredientFavoriteRepository.findCatalogIdsByUserId(userId);
		List<IngredientCatalog> mine = ingredientCatalogRepository.findAllByOwnerIdOrderByIdDesc(userId);
		Set<String> myNames = mine.stream().map(IngredientCatalog::getName).collect(Collectors.toSet());
		List<IngredientCatalog> basics = ingredientCatalogRepository.findAllByOwnerIsNullOrderByIdAsc().stream()
			.filter(catalog -> !myNames.contains(catalog.getName()))
			.toList();
		List<IngredientCatalogResponse> ingredients = Stream.concat(mine.stream(), basics.stream())
			.map(catalog -> IngredientCatalogResponse.from(catalog, favoriteIds.contains(catalog.getId())))
			// 정렬이 안정적이라 같은 그룹 안에서는 최근 순서가 유지된다
			.sorted(Comparator.comparing(IngredientCatalogResponse::favorite).reversed())
			.toList();
		return new IngredientCatalogListResponse(ingredients);
	}

	@Transactional
	public IngredientCatalogResponse create(Long userId, IngredientCatalogCreateRequest request) {
		String name = request.name().strip();
		// 기본 재료와 이름이 같으면 도감에 같은 재료가 두 개 보이므로 막는다
		if (ingredientCatalogRepository.existsByOwnerIdAndName(userId, name)
			|| ingredientCatalogRepository.existsByOwnerIsNullAndName(name)) {
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
		return IngredientCatalogResponse.from(catalog, false);
	}

	// 이미 즐겨찾기한 재료여도 오류 없이 같은 결과를 준다
	@Transactional
	public IngredientCatalogResponse addFavorite(Long userId, Long catalogId) {
		IngredientCatalog catalog = findUsableCatalog(userId, catalogId);
		if (!ingredientFavoriteRepository.existsById(new IngredientFavoriteId(userId, catalogId))) {
			ingredientFavoriteRepository.save(new IngredientFavorite(userRepository.getReferenceById(userId), catalog));
		}
		return IngredientCatalogResponse.from(catalog, true);
	}

	// 즐겨찾기하지 않은 재료여도 오류 없이 같은 결과를 준다
	@Transactional
	public IngredientCatalogResponse removeFavorite(Long userId, Long catalogId) {
		IngredientCatalog catalog = findUsableCatalog(userId, catalogId);
		ingredientFavoriteRepository.deleteById(new IngredientFavoriteId(userId, catalogId));
		return IngredientCatalogResponse.from(catalog, false);
	}

	// 사진을 바꿀 때마다 새 키를 만들어, 화면이 옛 주소로 받아 둔 사진을 오래 캐시해도 새 사진과 섞이지 않게 한다
	@Transactional
	public IngredientCatalogResponse changeImage(Long userId, Long catalogId, MultipartFile image) {
		byte[] png = ingredientImageProcessor.toPng(image);
		IngredientCatalog catalog = lockMyCatalog(userId, catalogId);
		String oldKey = catalog.getImageKey();
		String newKey = UUID.randomUUID().toString();
		ingredientImageRepository.save(new IngredientImage(newKey, IngredientImageProcessor.CONTENT_TYPE, png));
		catalog.changeImageKey(newKey);
		if (oldKey != null) {
			ingredientImageRepository.deleteById(oldKey);
		}
		return IngredientCatalogResponse.from(catalog, isFavorite(userId, catalogId));
	}

	// 사진이 없는 재료여도 오류 없이 이모지를 보여 주는 결과를 준다
	@Transactional
	public IngredientCatalogResponse removeImage(Long userId, Long catalogId) {
		IngredientCatalog catalog = lockMyCatalog(userId, catalogId);
		deleteImage(catalog);
		return IngredientCatalogResponse.from(catalog, isFavorite(userId, catalogId));
	}

	public IngredientImage getImage(String imageKey) {
		return ingredientImageRepository.findById(imageKey)
			.orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_IMAGE_NOT_FOUND));
	}

	@Transactional
	public void delete(Long userId, Long catalogId) {
		IngredientCatalog catalog = findMyCatalog(userId, catalogId);

		// 이 재료를 가리키는 행이 남아 있으면 외래 키 때문에 지워지지 않는다
		ingredientCatalogRepository.detachFridgeItems(catalogId);
		ingredientCatalogRepository.detachPurchasedItems(catalogId);
		ingredientCatalogRepository.detachShoppingListItems(catalogId);
		ingredientCatalogRepository.detachRecipeIngredients(catalogId);
		ingredientCatalogRepository.deleteFavorites(catalogId);
		deleteImage(catalog);
		ingredientCatalogRepository.delete(catalog);
	}

	private void deleteImage(IngredientCatalog catalog) {
		String imageKey = catalog.getImageKey();
		if (imageKey != null) {
			catalog.changeImageKey(null);
			ingredientImageRepository.deleteById(imageKey);
		}
	}

	private boolean isFavorite(Long userId, Long catalogId) {
		return ingredientFavoriteRepository.existsById(new IngredientFavoriteId(userId, catalogId));
	}

	// 기본 재료와 다른 사용자의 재료는 사진을 바꿀 수 없고, 있는지도 알 수 없게 없는 재료와 같은 오류를 준다
	private IngredientCatalog lockMyCatalog(Long userId, Long catalogId) {
		return ingredientCatalogRepository.findByIdAndOwnerIdForUpdate(catalogId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));
	}

	// 다른 사용자의 재료는 있는지도 알 수 없게 없는 재료와 같은 오류를 준다. 기본 재료도 지울 수 없어서 같은 오류를 준다
	private IngredientCatalog findMyCatalog(Long userId, Long catalogId) {
		return ingredientCatalogRepository.findByIdAndOwnerId(catalogId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));
	}

	// 내 재료와 기본 재료. 다른 사용자의 재료는 없는 재료와 같은 오류를 준다
	private IngredientCatalog findUsableCatalog(Long userId, Long catalogId) {
		return ingredientCatalogRepository.findUsableById(catalogId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_NOT_FOUND));
	}

}
