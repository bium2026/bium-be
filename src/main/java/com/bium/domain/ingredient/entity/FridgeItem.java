package com.bium.domain.ingredient.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bium.domain.fridge.entity.Fridge;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 소프트 삭제된 재료도 요리 되돌리기에서 다시 불러와야 해서 @SQLRestriction을 걸지 않고, 조회할 때 deleted_at으로 거른다
@Entity
@Table(name = "fridge_items", indexes = {
	@Index(columnList = "fridge_id, storage, section, sort_order"),
	@Index(columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal quantity;

	@Column(length = 10)
	private String unit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StorageType storage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SectionType section;

	@Column(nullable = false)
	private Integer sortOrder;

	private LocalDate purchasedAt;

	private LocalDate expiresAt;

	// 도감 값을 복사해 두고 재료별로 수정할 수 있게 한다
	private Integer purchaseCycleDays;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	// 공유 냉장고에서 여러 멤버가 같은 재료를 동시에 고치는 것을 막는다
	@Version
	@Column(nullable = false)
	private Integer version;

	private LocalDateTime deletedAt;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@UpdateTimestamp
	private LocalDateTime updatedAt;

	@Builder
	public FridgeItem(Fridge fridge, IngredientCatalog catalog, String name, BigDecimal quantity, String unit,
		StorageType storage, SectionType section, Integer sortOrder, LocalDate purchasedAt, LocalDate expiresAt,
		Integer purchaseCycleDays, User createdBy) {
		this.fridge = fridge;
		this.catalog = catalog;
		this.name = name;
		this.quantity = quantity;
		this.unit = unit;
		this.storage = storage;
		this.section = section;
		this.sortOrder = sortOrder;
		this.purchasedAt = purchasedAt;
		this.expiresAt = expiresAt;
		this.purchaseCycleDays = purchaseCycleDays;
		this.createdBy = createdBy;
	}

}
