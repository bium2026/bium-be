package com.bium.domain.ingredient.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.ingredient.entity.IngredientUserImage;
import com.bium.domain.ingredient.entity.IngredientUserImageId;

public interface IngredientUserImageRepository extends JpaRepository<IngredientUserImage, IngredientUserImageId> {

	@Query("select u from IngredientUserImage u where u.id.userId = :userId")
	List<IngredientUserImage> findAllByUserId(Long userId);

	// 목록 응답을 만들 때 재료마다 쿼리가 나가지 않게 보여 줄 기본 재료의 사진을 한 번에 읽는다
	@Query("select u from IngredientUserImage u where u.id.userId = :userId and u.id.catalogId in :catalogIds")
	List<IngredientUserImage> findAllByUserIdAndCatalogIdIn(Long userId, Collection<Long> catalogIds);

}
