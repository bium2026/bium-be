package com.bium.domain.fridge.entity;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fridges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fridge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	// 피드 공개 여부
	private boolean isPublic = false;

	// 멤버들이 장을 보는 요일. 정하지 않았으면 null이다
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private DayOfWeek shoppingDay;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@UpdateTimestamp
	private LocalDateTime updatedAt;

	public Fridge(String name) {
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}

	// 주인이 피드에 냉장고를 공개하거나 숨긴다
	public void changeVisibility(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public void changeShoppingDay(DayOfWeek shoppingDay) {
		this.shoppingDay = shoppingDay;
	}

}
