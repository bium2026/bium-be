package com.bium.domain.ingredient.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.ingredient.dto.IngredientImageLookup;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientUserImage;
import com.bium.domain.ingredient.repository.IngredientUserImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 응답에 사진 주소를 담기 전에, 응답에 나올 기본 재료 중 사용자가 사진을 올린 것을 한 번에 읽어 둔다.
 * 기본 재료가 하나도 없으면 쿼리를 보내지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientImageLookupService {

	private final IngredientUserImageRepository ingredientUserImageRepository;

	public IngredientImageLookup forCatalogs(Long userId, Collection<IngredientCatalog> catalogs) {
		List<Long> basicIds = catalogs.stream()
			.filter(Objects::nonNull)
			.filter(IngredientCatalog::isShared)
			.map(IngredientCatalog::getId)
			.distinct()
			.toList();
		if (basicIds.isEmpty()) {
			return IngredientImageLookup.NONE;
		}
		return toLookup(ingredientUserImageRepository.findAllByUserIdAndCatalogIdIn(userId, basicIds));
	}

	public IngredientImageLookup forCatalog(Long userId, IngredientCatalog catalog) {
		return forCatalogs(userId, catalog == null ? List.of() : List.of(catalog));
	}

	public IngredientImageLookup forItems(Long userId, Collection<FridgeItem> items) {
		return forCatalogs(userId, items.stream().map(FridgeItem::getCatalog).toList());
	}

	public IngredientImageLookup forItem(Long userId, FridgeItem item) {
		return forCatalog(userId, item.getCatalog());
	}

	// 도감 목록처럼 기본 재료를 모두 보여 줄 때는 재료 id를 모으지 않고 사용자의 사진을 모두 읽는다
	public IngredientImageLookup forAllCatalogs(Long userId) {
		return toLookup(ingredientUserImageRepository.findAllByUserId(userId));
	}

	private IngredientImageLookup toLookup(List<IngredientUserImage> images) {
		Map<Long, String> keys = images.stream()
			.collect(Collectors.toMap(image -> image.getId().getCatalogId(), IngredientUserImage::getImageKey));
		return new IngredientImageLookup(keys);
	}

}
