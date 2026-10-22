package com.bium.domain.ingredient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.ingredient.entity.FridgeItem;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNull(Long fridgeId);

	Optional<FridgeItem> findByIdAndDeletedAtIsNull(Long id);

}
