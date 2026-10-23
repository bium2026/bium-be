package com.bium.domain.ingredient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.ingredient.entity.FridgeItem;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNull(Long fridgeId);

	// 칸 안에서 보여 줄 순서대로 가져온다. 순서가 같으면 먼저 넣은 재료가 앞에 온다
	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long fridgeId);

	Optional<FridgeItem> findByIdAndDeletedAtIsNull(Long id);

}
