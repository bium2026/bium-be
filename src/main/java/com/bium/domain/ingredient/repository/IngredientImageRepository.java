package com.bium.domain.ingredient.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.ingredient.entity.IngredientImage;

public interface IngredientImageRepository extends JpaRepository<IngredientImage, String> {
}
