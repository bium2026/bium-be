package com.bium.domain.ingredient.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.ingredient.entity.IngredientFavorite;
import com.bium.domain.ingredient.entity.IngredientFavoriteId;

public interface IngredientFavoriteRepository extends JpaRepository<IngredientFavorite, IngredientFavoriteId> {

	@Query("select f.id.catalogId from IngredientFavorite f where f.id.userId = :userId")
	Set<Long> findCatalogIdsByUserId(Long userId);

}
