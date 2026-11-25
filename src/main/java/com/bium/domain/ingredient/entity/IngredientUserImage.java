package com.bium.domain.ingredient.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 사용자가 같이 쓰는 기본 재료에 한 사용자가 올린 사진.
 * 기본 재료의 이모지는 그대로 두고, 이 사진은 올린 사용자의 화면에서만 이모지 대신 보인다.
 * 사진 자체는 ingredient_images에 있고 여기에는 그 키만 둔다.
 */
@Entity
@Table(name = "ingredient_user_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientUserImage {

	@EmbeddedId
	private IngredientUserImageId id;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@MapsId("catalogId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	@Column(nullable = false, length = 36)
	private String imageKey;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public IngredientUserImage(User user, IngredientCatalog catalog, String imageKey) {
		this.id = new IngredientUserImageId(user.getId(), catalog.getId());
		this.user = user;
		this.catalog = catalog;
		this.imageKey = imageKey;
	}

	public void changeImageKey(String imageKey) {
		this.imageKey = imageKey;
	}

}
