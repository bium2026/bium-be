package com.bium.domain.recipe.entity;

import java.math.BigDecimal;

import com.bium.domain.ingredient.entity.IngredientCatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(precision = 8, scale = 2)
	private BigDecimal quantity;

	@Column(length = 10)
	private String unit;

	private boolean isRequired = true;

	@Builder
	public RecipeIngredient(Recipe recipe, IngredientCatalog catalog, String name, BigDecimal quantity, String unit,
		Boolean isRequired) {
		this.recipe = recipe;
		this.catalog = catalog;
		this.name = name;
		this.quantity = quantity;
		this.unit = unit;
		if (isRequired != null) {
			this.isRequired = isRequired;
		}
	}

}
