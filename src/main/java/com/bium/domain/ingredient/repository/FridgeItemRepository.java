package com.bium.domain.ingredient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;

import jakarta.persistence.LockModeType;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNull(Long fridgeId);

	// 칸 안에서 보여 줄 순서대로 가져온다. 순서가 같으면 먼저 넣은 재료가 앞에 온다
	List<FridgeItem> findAllByFridgeIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long fridgeId);

	// 한 칸의 재료를 보여 주는 순서대로 가져온다
	List<FridgeItem> findAllByFridgeIdAndStorageAndSectionAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long fridgeId,
		StorageType storage, SectionType section);

	Optional<FridgeItem> findByIdAndDeletedAtIsNull(Long id);

	// 잠그는 조회는 MySQL에서 스냅샷 없이 최신 값을 읽어서, 옮기기 전에 버전과 삭제 여부를 정확히 확인할 수 있다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from FridgeItem i where i.id = :id and i.deletedAt is null")
	Optional<FridgeItem> findByIdForUpdate(Long id);

	// 칸에 재료가 없으면 null
	@Query("select max(i.sortOrder) from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage"
		+ " and i.section = :section and i.deletedAt is null")
	Integer findMaxSortOrder(Long fridgeId, StorageType storage, SectionType section);

}
