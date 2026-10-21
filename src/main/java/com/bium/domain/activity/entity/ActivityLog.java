package com.bium.domain.activity.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity_logs", indexes = @Index(columnList = "fridge_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_item_id")
	private FridgeItem fridgeItem;

	// 재료가 삭제된 뒤에도 기록에 이름이 보이도록 저장해 둔다
	@Column(length = 50)
	private String itemName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private ActivityAction action;

	@Column(columnDefinition = "json")
	private String detail;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@Builder
	public ActivityLog(Fridge fridge, User user, FridgeItem fridgeItem, String itemName, ActivityAction action,
		String detail) {
		this.fridge = fridge;
		this.user = user;
		this.fridgeItem = fridgeItem;
		this.itemName = itemName;
		this.action = action;
		this.detail = detail;
	}

}
