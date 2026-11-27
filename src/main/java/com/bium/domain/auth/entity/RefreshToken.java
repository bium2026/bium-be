package com.bium.domain.auth.entity;

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

/**
 * 액세스 토큰이 만료됐을 때 다시 로그인하지 않고 새 토큰을 받는 데 쓰는 리프레시 토큰이다.
 * DB가 새어도 토큰으로 쓸 수 없게 토큰 원문이 아닌 SHA-256 해시만 저장한다.
 * 갱신에 쓰면 지우고 새 토큰을 주며, 로그아웃·비밀번호 변경·탈퇴 때도 지운다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 토큰 원문의 SHA-256 해시(16진수 64자)
	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}

}
