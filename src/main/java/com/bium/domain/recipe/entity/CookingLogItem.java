package com.bium.domain.recipe.entity;

import java.math.BigDecimal;

import com.bium.domain.ingredient.entity.FridgeItem;

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

// 차감 기록(상세). 되돌릴 때는 다른 멤버의 수정이 사라지지 않도록 quantityBefore가 아니라 현재 수량 + deductedQuantity로 복원한다
@Entity
@Table(name = "cooking_log_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CookingLogItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cooking_log_id", nullable = false)
	private CookingLog cookingLog;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_item_id", nullable = false)
	private FridgeItem fridgeItem;

	// 기록용
	@Column(precision = 8, scale = 2)
	private BigDecimal quantityBefore;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal deductedQuantity;

	// 이 차감으로 수량이 0이 되어 소프트 삭제됐는지. true면 되돌릴 때 재료를 다시 살린다
	@Column(nullable = false)
	private boolean deletedByDeduction;

	@Builder
	public CookingLogItem(CookingLog cookingLog, FridgeItem fridgeItem, BigDecimal quantityBefore,
		BigDecimal deductedQuantity, boolean deletedByDeduction) {
		this.cookingLog = cookingLog;
		this.fridgeItem = fridgeItem;
		this.quantityBefore = quantityBefore;
		this.deductedQuantity = deductedQuantity;
		this.deletedByDeduction = deletedByDeduction;
	}

}
