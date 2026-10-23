package com.bium.domain.shopping.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.shopping.entity.PurchasedItem;

public interface PurchasedItemRepository extends JpaRepository<PurchasedItem, Long> {

	// 냉장고로 옮긴 장본 음식은 기록으로만 남아 있어 빼고 가져온다
	List<PurchasedItem> findAllByFridgeIdAndMovedAtIsNullOrderByIdDesc(Long fridgeId);

	Optional<PurchasedItem> findByIdAndMovedAtIsNull(Long id);

}
