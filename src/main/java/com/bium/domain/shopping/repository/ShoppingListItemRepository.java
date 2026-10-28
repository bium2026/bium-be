package com.bium.domain.shopping.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.shopping.entity.ShoppingListItem;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

	// 항목마다 레시피 이름을 꺼낼 때 쿼리가 따로 나가지 않게 레시피를 함께 불러온다.
	// 안 산 것을 먼저 보여 주고, 그 안에서는 최근에 담은 순서로 준다. 담은 시각이 같으면 id로 가른다
	@Query("select s from ShoppingListItem s left join fetch s.recipe where s.fridge.id = :fridgeId "
		+ "order by s.isChecked asc, s.createdAt desc, s.id desc")
	List<ShoppingListItem> findAllWithRecipeByFridgeId(Long fridgeId);

	// 이미 담아 두고 아직 안 산 재료를 다시 담지 않으려고 쓴다
	List<ShoppingListItem> findAllByFridgeIdAndIsCheckedFalse(Long fridgeId);

}
