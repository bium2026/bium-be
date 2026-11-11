package com.bium.domain.ingredient.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 도감 즐겨찾기의 복합 키 (user_id, catalog_id)
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IngredientFavoriteId implements Serializable {

	private Long userId;

	private Long catalogId;

}
