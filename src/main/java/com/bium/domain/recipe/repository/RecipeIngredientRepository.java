package com.bium.domain.recipe.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.recipe.entity.RecipeIngredient;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

	List<RecipeIngredient> findAllByRecipeIdInOrderByIdAsc(List<Long> recipeIds);

}
