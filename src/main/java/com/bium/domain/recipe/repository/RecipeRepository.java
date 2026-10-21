package com.bium.domain.recipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.recipe.entity.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
