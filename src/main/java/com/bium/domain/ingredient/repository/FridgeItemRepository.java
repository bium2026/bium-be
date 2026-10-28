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

	// 순번을 다시 매길 칸의 재료를 보여 주는 순서대로 잠그고 읽는다. 잠그는 조회는 MySQL에서 스냅샷 없이 최신 값을 읽어서
	// 앞선 요청이 커밋한 순번과 새 재료까지 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage and i.section = :section"
		+ " and i.deletedAt is null order by i.sortOrder, i.id")
	List<FridgeItem> findAllInSectionForUpdate(Long fridgeId, StorageType storage, SectionType section);

	Optional<FridgeItem> findByIdAndDeletedAtIsNull(Long id);

	// 냉장고를 먼저 잠그려고 재료를 잠그기 전에 냉장고 id만 읽는다. 재료 엔티티를 읽어 두면 뒤에서 잠그며 다시 읽어도
	// 처음 읽은 값을 그대로 쓰게 된다
	@Query("select i.fridge.id from FridgeItem i where i.id = :id and i.deletedAt is null")
	Optional<Long> findFridgeIdById(Long id);

	// 냉장고를 잠근 뒤에 부른다. 잠그는 조회라 그사이 바뀐 버전과 삭제 여부까지 최신 값으로 읽는다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from FridgeItem i where i.id = :id and i.deletedAt is null")
	Optional<FridgeItem> findByIdForUpdate(Long id);

	// 칸에 재료가 없으면 null
	@Query("select max(i.sortOrder) from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage"
		+ " and i.section = :section and i.deletedAt is null")
	Integer findMaxSortOrder(Long fridgeId, StorageType storage, SectionType section);

}
