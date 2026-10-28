package com.bium.domain.shopping.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.shopping.entity.PurchasedItem;

import jakarta.persistence.LockModeType;

public interface PurchasedItemRepository extends JpaRepository<PurchasedItem, Long> {

	// 냉장고로 옮긴 장본 음식은 기록으로만 남아 있어 빼고 가져온다
	List<PurchasedItem> findAllByFridgeIdAndMovedAtIsNullOrderByIdDesc(Long fridgeId);

	Optional<PurchasedItem> findByIdAndMovedAtIsNull(Long id);

	// 냉장고를 먼저 잠그려고 장본 음식을 잠그기 전에 냉장고 id만 읽는다
	@Query("select p.fridge.id from PurchasedItem p where p.id = :id and p.movedAt is null")
	Optional<Long> findFridgeIdById(Long id);

	// 같은 장본 음식을 동시에 두 번 옮겨 냉장고 재료가 두 개 생기지 않게 잠그고 읽는다. 냉장고를 잠근 뒤에 부른다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PurchasedItem p where p.id = :id and p.movedAt is null")
	Optional<PurchasedItem> findByIdForUpdate(Long id);

}
