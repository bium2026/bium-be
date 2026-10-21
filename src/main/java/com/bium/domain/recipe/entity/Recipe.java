package com.bium.domain.recipe.entity;

import java.time.LocalDateTime;

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

	private String summary;

	// 조리 단계 JSON 배열. Gemini 응답을 검증한 뒤 직렬화해서 넣는다
	@Column(columnDefinition = "json")
	private String steps;

	private Integer cookMinutes;

	private Integer servings;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@Builder
	public Recipe(Fridge fridge, User requestedBy, RecipeType type, String title, String summary, String steps,
		Integer cookMinutes, Integer servings) {
		this.fridge = fridge;
		this.requestedBy = requestedBy;
		this.type = type;
		this.title = title;
		this.summary = summary;
		this.steps = steps;
		this.cookMinutes = cookMinutes;
		this.servings = servings;
	}

}
