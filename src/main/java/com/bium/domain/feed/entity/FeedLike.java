package com.bium.domain.feed.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.fridge.entity.Fridge;
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
@Table(name = "feed_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedLike {

	@EmbeddedId
	private FeedId id;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@MapsId("fridgeId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id")
	private Fridge fridge;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public FeedLike(User user, Fridge fridge) {
		this.id = new FeedId(user.getId(), fridge.getId());
		this.user = user;
		this.fridge = fridge;
	}

}
