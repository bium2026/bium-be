package com.bium.domain.feed.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 피드 냉장고에 남긴 코멘트. dishName이 있으면 그 음식을 추천한 코멘트다
@Entity
@Table(name = "feed_comments", indexes = @Index(columnList = "fridge_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 200)
	private String content;

	@Column(length = 50)
	private String dishName;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public FeedComment(Fridge fridge, User user, String content, String dishName) {
		this.fridge = fridge;
		this.user = user;
		this.content = content;
		this.dishName = dishName;
	}

	public void edit(String content, String dishName) {
		this.content = content;
		this.dishName = dishName;
	}

}
