package com.bium.domain.feed.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 피드 좋아요·저장의 복합 키 (user_id, fridge_id)
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FeedId implements Serializable {

	private Long userId;

	private Long fridgeId;

}
