package com.bium.domain.ingredient.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 컬럼 이름 끝의 단위(_g)는 기본 네이밍 전략이 언더스코어로 바꾸지 못해서 직접 지정한다
@Entity
@Table(name = "nutrition_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutritionInfo {

	@Id
	private Long catalogId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	// 식품영양성분 DB 식품코드
	@Column(length = 30)
	private String foodCode;

	@Column(name = "base_amount_g", precision = 7, scale = 2)
	private BigDecimal baseAmountG = BigDecimal.valueOf(100);

	@Column(name = "calories_kcal", precision = 7, scale = 2)
	private BigDecimal caloriesKcal;

	@Column(name = "carbohydrate_g", precision = 7, scale = 2)
	private BigDecimal carbohydrateG;

	@Column(name = "protein_g", precision = 7, scale = 2)
	private BigDecimal proteinG;

	@Column(name = "fat_g", precision = 7, scale = 2)
	private BigDecimal fatG;

	@UpdateTimestamp
	private LocalDateTime fetchedAt;

	@Builder
	public NutritionInfo(IngredientCatalog catalog, String foodCode, BigDecimal baseAmountG, BigDecimal caloriesKcal,
		BigDecimal carbohydrateG, BigDecimal proteinG, BigDecimal fatG) {
		this.catalog = catalog;
		this.foodCode = foodCode;
		if (baseAmountG != null) {
			this.baseAmountG = baseAmountG;
		}
		this.caloriesKcal = caloriesKcal;
		this.carbohydrateG = carbohydrateG;
		this.proteinG = proteinG;
		this.fatG = fatG;
	}

}
