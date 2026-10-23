package com.bium.domain.ingredient.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bium.domain.user.entity.User;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingredient_catalog", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_key", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientCatalog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// NULL이면 공용 도감, 값이 있으면 사용자 커스텀
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	// MySQL unique는 NULL끼리 중복으로 보지 않아서, 공용 도감(owner_id NULL)의 이름 중복을 막으려고 0으로 바꾼 생성 컬럼에 unique를 건다
	@Column(nullable = false, insertable = false, updatable = false,
		columnDefinition = "bigint generated always as (ifnull(owner_id, 0))")
	private Long ownerKey;

	@Column(nullable = false, length = 50)
	private String name;

	// 도감 화면에 보여 줄 아이콘 이모지
	@Column(length = 16)
	private String emoji;

	// ERD대로 varchar(30)에 저장한다. @Enumerated만 두면 MySQL enum 컬럼으로 만들어진다
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private IngredientCategory category;

	@Enumerated(EnumType.STRING)
	private StorageType defaultStorage;

	// 기본 유통기한(일)
	private Integer shelfLifeDays;

	// 기본 구매 주기(일)
	private Integer purchaseCycleDays;

	@Column(length = 10)
	private String defaultUnit;

	@Builder
	public IngredientCatalog(User owner, String name, String emoji, IngredientCategory category,
		StorageType defaultStorage, Integer shelfLifeDays, Integer purchaseCycleDays, String defaultUnit) {
		this.owner = owner;
		this.name = name;
		this.emoji = emoji;
		this.category = category;
		this.defaultStorage = defaultStorage;
		this.shelfLifeDays = shelfLifeDays;
		this.purchaseCycleDays = purchaseCycleDays;
		this.defaultUnit = defaultUnit;
	}

	// 장 본 날부터 기본 유통기한만큼 더한 날을 유통기한으로 삼는다
	public LocalDate calculateExpiresAt(LocalDate purchasedAt) {
		return shelfLifeDays == null ? null : purchasedAt.plusDays(shelfLifeDays);
	}

}
