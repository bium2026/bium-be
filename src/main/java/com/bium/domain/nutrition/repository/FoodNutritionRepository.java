package com.bium.domain.nutrition.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.nutrition.entity.FoodNutrition;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, Long> {

	Optional<FoodNutrition> findBySearchName(String searchName);

}
