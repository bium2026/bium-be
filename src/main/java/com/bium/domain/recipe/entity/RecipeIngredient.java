package com.bium.domain.recipe.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bium.domain.ingredient.entity.IngredientCatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	// AI가 주는 "1/2모", "1큰술" 같은 분량. quantity와 unit으로 나눌 수 없는 값을 그대로 둔다
	@Column(length = 50)
	private String amount;

	// 냉장고 재료인지, 기본 양념인지, 냉장고에 없어 사야 하는 재료인지
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private IngredientSource source;

	private boolean isRequired = true;

	@Builder
	public RecipeIngredient(Recipe recipe, IngredientCatalog catalog, String name, BigDecimal quantity, String unit,
		String amount, IngredientSource source, Boolean isRequired) {
		this.recipe = recipe;
		this.catalog = catalog;
		this.name = name;
		this.quantity = quantity;
		this.unit = unit;
		this.amount = amount;
		this.source = source;
		if (isRequired != null) {
			this.isRequired = isRequired;
		}
	}

}
