package com.bium.domain.shopping.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장보기 목록. 냉장고 멤버가 함께 쓴다
@Entity
@Table(name = "shopping_list_items", indexes = @Index(columnList = "fridge_id, is_checked"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShoppingListItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	// 목록에 담은 사람
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	// 부족 재료 추천에서 담은 경우
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id")
	private Recipe recipe;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(precision = 8, scale = 2)
	private BigDecimal quantity;

	@Column(length = 10)
	private String unit;

	private boolean isChecked = false;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@Builder
	public ShoppingListItem(Fridge fridge, User createdBy, IngredientCatalog catalog, Recipe recipe, String name,
		BigDecimal quantity, String unit) {
		this.fridge = fridge;
		this.createdBy = createdBy;
		this.catalog = catalog;
		this.recipe = recipe;
		this.name = name;
		this.quantity = quantity;
		this.unit = unit;
	}

}
