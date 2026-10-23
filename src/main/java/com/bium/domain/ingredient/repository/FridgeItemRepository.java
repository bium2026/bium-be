package com.bium.domain.ingredient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNull(Long fridgeId);

	Optional<FridgeItem> findByIdAndDeletedAtIsNull(Long id);

	// 칸에 재료가 없으면 null
	@Query("select max(i.sortOrder) from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage"
		+ " and i.section = :section and i.deletedAt is null")
	Integer findMaxSortOrder(Long fridgeId, StorageType storage, SectionType section);

}
