package com.bium.domain.ingredient.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 기본 재료에 사용자가 올린 사진의 복합 키 (user_id, catalog_id)
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IngredientUserImageId implements Serializable {

	private Long userId;

	private Long catalogId;

}
