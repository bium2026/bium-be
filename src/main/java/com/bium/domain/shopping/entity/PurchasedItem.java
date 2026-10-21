package com.bium.domain.shopping.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장본 음식. 냉장고에 넣기 전 단계이고, 넣으면 fridgeItem과 movedAt이 채워진다
@Entity
@Table(name = "purchased_items", indexes = @Index(columnList = "fridge_id, moved_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchasedItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 넣을 냉장고
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	// 장 본 사람
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(precision = 8, scale = 2)
	private BigDecimal quantity;

	@Column(length = 10)
	private String unit;

	@Enumerated(EnumType.STRING)
	private StorageType storage;

	private LocalDate purchasedAt;

	private LocalDate expiresAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private PurchaseSource source;

	// 같은 장본 음식이 두 번 옮겨지지 않도록 unique
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_item_id", unique = true)
	private FridgeItem fridgeItem;

	private LocalDateTime movedAt;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@Builder
	public PurchasedItem(Fridge fridge, User user, IngredientCatalog catalog, String name, BigDecimal quantity,
		String unit, StorageType storage, LocalDate purchasedAt, LocalDate expiresAt, PurchaseSource source) {
		this.fridge = fridge;
		this.user = user;
		this.catalog = catalog;
		this.name = name;
		this.quantity = quantity;
		this.unit = unit;
		this.storage = storage;
		this.purchasedAt = purchasedAt;
		this.expiresAt = expiresAt;
		this.source = source;
	}

}
