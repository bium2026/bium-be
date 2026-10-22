package com.bium.domain.recipe.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by")
	private User requestedBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private RecipeType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(length = 16)
	private String emoji;

	private String summary;

	// 조리 단계 JSON 배열. Gemini 응답을 검증한 뒤 넣는다
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> steps;

	// 조리 팁 JSON 배열
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> tips;

	private Integer cookMinutes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private RecipeDifficulty difficulty;

	private Integer servings;

	private Integer caloriesKcal;

	// 1인분 영양 정보(g). AI가 주지 않으면 비어 있다
	private Integer proteinG;

	private Integer carbohydrateG;

	private Integer fatG;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@Builder
	public Recipe(Fridge fridge, User requestedBy, RecipeType type, String title, String emoji, String summary,
		List<String> steps, List<String> tips, Integer cookMinutes, RecipeDifficulty difficulty, Integer servings,
		Integer caloriesKcal, Integer proteinG, Integer carbohydrateG, Integer fatG) {
		this.fridge = fridge;
		this.requestedBy = requestedBy;
		this.type = type;
		this.title = title;
		this.emoji = emoji;
		this.summary = summary;
		this.steps = steps;
		this.tips = tips;
		this.cookMinutes = cookMinutes;
		this.difficulty = difficulty;
		this.servings = servings;
		this.caloriesKcal = caloriesKcal;
		this.proteinG = proteinG;
		this.carbohydrateG = carbohydrateG;
		this.fatG = fatG;
	}

}
