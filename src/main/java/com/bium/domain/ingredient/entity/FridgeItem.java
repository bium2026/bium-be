package com.bium.domain.ingredient.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;
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

// 소프트 삭제된 재료도 요리 되돌리기에서 다시 불러와야 해서 @SQLRestriction을 걸지 않고, 조회할 때 deleted_at으로 거른다.
// 순번은 버전을 올리지 않아서, 모든 열을 쓰면 순번이 바뀌기 전에 불러온 재료를 고칠 때 예전 순번으로 되돌아간다.
// 그래서 바뀐 열만 UPDATE한다
@DynamicUpdate
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

	// 냉장고 화면에 보여 줄 아이콘 이모지
	@Column(length = 16)
	private String emoji;

	// 수량을 세지 않는 재료(화면의 "있음")는 열이 NOT NULL이라 1로 두고 단위를 비운다. 단위가 있어야 수량을 센다(hasQuantity)
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

	// 재료 하나를 옮기면 같은 칸 재료의 순번이 함께 바뀐다. 그때마다 버전이 오르면 다른 멤버의 이름·유통기한 수정이
	// 충돌로 막히므로 순번 변경은 버전을 올리지 않는다. 순번은 냉장고 행을 잠가 한 번에 한 요청만 바꾼다
	@OptimisticLock(excluded = true)
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
	public FridgeItem(Fridge fridge, IngredientCatalog catalog, String name, String emoji, BigDecimal quantity,
		String unit, StorageType storage, SectionType section, Integer sortOrder, LocalDate purchasedAt,
		LocalDate expiresAt, Integer purchaseCycleDays, User createdBy) {
		this.fridge = fridge;
		this.catalog = catalog;
		this.name = name;
		this.emoji = emoji;
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

	// 재료 수정 모달에서 고칠 수 있는 값만 바꾼다. quantity가 null이면 수량을 세지 않는 재료로 바꾸고 unit은 쓰지 않는다
	public void update(String name, String emoji, LocalDate expiresAt, BigDecimal quantity, String unit) {
		this.name = name;
		this.emoji = emoji;
		this.expiresAt = expiresAt;
		// 바꾼 것 없이 저장하면 버전이 오르지 않게, 300과 300.00처럼 같은 수량이거나 이미 수량을 세지 않는 재료면 그대로 둔다
		if (quantity != null) {
			if (this.quantity.compareTo(quantity) != 0) {
				this.quantity = quantity;
			}
			this.unit = unit;
		} else if (hasQuantity()) {
			this.quantity = BigDecimal.ONE;
			this.unit = null;
		}
	}

	public boolean hasQuantity() {
		return unit != null;
	}

	public void delete(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	// 다른 보관 구역이나 칸으로 옮긴다. 칸 안 순번은 changeSortOrder로 따로 매긴다
	public void moveTo(StorageType storage, SectionType section) {
		this.storage = storage;
		this.section = section;
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	// 요리에 쓴 만큼 줄이고 뺀 수량을 돌려준다. amount가 null(다 씀)이거나 남은 수량 이상이거나
	// 수량을 세지 않는 재료면 모두 빼서 냉장고에서 지운다
	public BigDecimal use(BigDecimal amount, LocalDateTime usedAt) {
		if (amount == null || !hasQuantity() || amount.compareTo(quantity) >= 0) {
			BigDecimal used = quantity;
			this.quantity = BigDecimal.ZERO;
			this.deletedAt = usedAt;
			return used;
		}
		this.quantity = quantity.subtract(amount);
		return amount;
	}

	// 요리 되돌리기. 그사이 다른 멤버가 바꾼 수량을 덮어쓰지 않도록 현재 수량에 뺀 만큼 더한다
	public void restore(BigDecimal deductedQuantity, boolean deletedByDeduction) {
		this.quantity = quantity.add(deductedQuantity);
		if (deletedByDeduction) {
			this.deletedAt = null;
		}
	}

}
