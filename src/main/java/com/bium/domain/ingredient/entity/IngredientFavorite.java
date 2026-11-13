package com.bium.domain.ingredient.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.user.entity.User;

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

@Entity
@Table(name = "ingredient_favorites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientFavorite {

	@EmbeddedId
	private IngredientFavoriteId id;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@MapsId("catalogId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "catalog_id")
	private IngredientCatalog catalog;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public IngredientFavorite(User user, IngredientCatalog catalog) {
		this.id = new IngredientFavoriteId(user.getId(), catalog.getId());
		this.user = user;
		this.catalog = catalog;
	}

}
