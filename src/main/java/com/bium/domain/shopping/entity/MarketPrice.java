package com.bium.domain.shopping.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// KAMIS 소매가 시세. 품목마다 품종·등급 중 가장 싼 가격 하나만 두고 매일 덮어쓴다
@Entity
@Table(name = "market_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 장보기 항목 이름과 그대로 맞춰 본다
	@Column(nullable = false, unique = true, length = 50)
	private String itemName;

	@Column(length = 50)
	private String kindName;

	// 상품, 중품 같은 KAMIS 등급
	@Column(length = 20)
	private String grade;

	@Column(length = 20)
	private String unit;

	@Column(nullable = false)
	private int price;

	private Integer weekAgoPrice;

	@Column(nullable = false)
	private LocalDate surveyDate;

	@UpdateTimestamp
	private LocalDateTime fetchedAt;

	@Builder
	public MarketPrice(String itemName, String kindName, String grade, String unit, int price, Integer weekAgoPrice,
		LocalDate surveyDate) {
		this.itemName = itemName;
		this.kindName = kindName;
		this.grade = grade;
		this.unit = unit;
		this.price = price;
		this.weekAgoPrice = weekAgoPrice;
		this.surveyDate = surveyDate;
	}

	public void update(String kindName, String grade, String unit, int price, Integer weekAgoPrice,
		LocalDate surveyDate) {
		this.kindName = kindName;
		this.grade = grade;
		this.unit = unit;
		this.price = price;
		this.weekAgoPrice = weekAgoPrice;
		this.surveyDate = surveyDate;
	}

}
