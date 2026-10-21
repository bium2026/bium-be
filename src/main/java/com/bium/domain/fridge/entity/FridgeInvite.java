package com.bium.domain.fridge.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fridge_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fridge_id", nullable = false)
	private Fridge fridge;

	@Column(nullable = false, unique = true, length = 12)
	private String code;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	private LocalDateTime expiresAt;

	private boolean isActive = true;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public FridgeInvite(Fridge fridge, String code, User createdBy, LocalDateTime expiresAt) {
		this.fridge = fridge;
		this.code = code;
		this.createdBy = createdBy;
		this.expiresAt = expiresAt;
	}

}
