package com.bium.domain.recipe.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.recipe.entity.CookingLogItem;

public interface CookingLogItemRepository extends JpaRepository<CookingLogItem, Long> {

	// 되돌릴 때 재료마다 쿼리가 나가지 않게 냉장고 재료를 함께 불러온다
	@Query("select i from CookingLogItem i join fetch i.fridgeItem where i.cookingLog.id = :cookingLogId order by i.id")
	List<CookingLogItem> findAllWithFridgeItemByCookingLogId(Long cookingLogId);

}
