package com.bium.domain.recipe.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 요리 완료 후 재료 차감 기록(헤더)
@Entity
@Table(name = "cooking_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CookingLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@CreationTimestamp
	private LocalDateTime cookedAt;

	// 값이 있으면 이미 되돌린 기록이라 다시 되돌릴 수 없다
	private LocalDateTime undoneAt;

	public CookingLog(Fridge fridge, Recipe recipe, User user) {
		this.fridge = fridge;
		this.recipe = recipe;
		this.user = user;
	}

}
