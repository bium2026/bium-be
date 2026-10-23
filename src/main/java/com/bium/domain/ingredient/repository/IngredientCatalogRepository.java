package com.bium.domain.ingredient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.ingredient.entity.IngredientCatalog;

public interface IngredientCatalogRepository extends JpaRepository<IngredientCatalog, Long> {

	List<IngredientCatalog> findAllByOwnerIdOrderByIdDesc(Long ownerId);

	Optional<IngredientCatalog> findByIdAndOwnerId(Long id, Long ownerId);

	boolean existsByOwnerIdAndName(Long ownerId, String name);

	// 아래 테이블들은 재료 이름을 따로 저장해 두어서, 도감 재료를 지울 때 행은 남기고 연결만 끊는다

	@Modifying
	@Query("update FridgeItem i set i.catalog = null where i.catalog.id = :catalogId")
	void detachFridgeItems(Long catalogId);

	@Modifying
	@Query("update PurchasedItem i set i.catalog = null where i.catalog.id = :catalogId")
	void detachPurchasedItems(Long catalogId);

	@Modifying
	@Query("update ShoppingListItem i set i.catalog = null where i.catalog.id = :catalogId")
	void detachShoppingListItems(Long catalogId);

	@Modifying
	@Query("update RecipeIngredient i set i.catalog = null where i.catalog.id = :catalogId")
	void detachRecipeIngredients(Long catalogId);

	@Modifying
	@Query("delete from NutritionInfo n where n.catalogId = :catalogId")
	void deleteNutritionInfo(Long catalogId);

}
