package com.bium.domain.ingredient.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 도감 재료에 이모지 대신 올린 사진. 서버가 작은 PNG로 다시 만든 것만 저장한다
@Entity
@Table(name = "ingredient_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientImage {

	// 이미지 주소에 그대로 들어가는 무작위 키. 로그인 없이 받는 주소라서 추측할 수 없어야 한다
	@Id
	@Column(length = 36)
	private String imageKey;

	@Column(nullable = false, length = 20)
	private String contentType;

	// 256px PNG라 1MB를 넘지 않는다. 길이를 주면 MySQL에서 mediumblob으로 만들어진다
	@Lob
	@Column(nullable = false, length = 1_000_000)
	private byte[] data;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public IngredientImage(String imageKey, String contentType, byte[] data) {
		this.imageKey = imageKey;
		this.contentType = contentType;
		this.data = data;
	}

}
