package com.bium.domain.recipe.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.recipe.entity.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

	// 지운 레시피는 빼고 cursor보다 id가 작은 레시피를 최신순으로 준다
	@Query("select r from Recipe r where r.fridge.id = :fridgeId and r.deletedAt is null and r.id < :cursor"
		+ " order by r.id desc")
	List<Recipe> findPageByFridgeIdBefore(Long fridgeId, Long cursor, Pageable pageable);

}
