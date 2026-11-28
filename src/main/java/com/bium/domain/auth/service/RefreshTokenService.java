package com.bium.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.entity.RefreshToken;
import com.bium.domain.auth.repository.RefreshTokenRepository;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

/**
 * 리프레시 토큰을 만들고, 갱신할 때 바꾸고(회전), 로그아웃·비밀번호 변경·탈퇴 때 지운다.
 * 토큰은 무작위 32바이트라 추측할 수 없고, DB에는 해시만 남긴다.
 */
@Service
@Transactional
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;
	private final Duration refreshTokenValidity;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Clock clock,
		@Value("${jwt.refresh-token-validity}") Duration refreshTokenValidity) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.clock = clock;
		this.refreshTokenValidity = refreshTokenValidity;
	}

	public String issue(User user) {
		LocalDateTime now = LocalDateTime.now(clock);
		refreshTokenRepository.deleteExpiredByUserId(user.getId(), now);
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		refreshTokenRepository.save(new RefreshToken(user, hash(token), now.plus(refreshTokenValidity)));
		return token;
	}

	// 받은 토큰은 지우고 같은 사람에게 새 토큰을 준다. 한 번 쓴 토큰은 다시 쓸 수 없다.
	// 없거나(로그아웃·갱신에 이미 씀) 만료된 토큰이면 다시 로그인해야 한다
	public RotatedRefreshToken rotate(String token) {
		RefreshToken saved = refreshTokenRepository.findByTokenHashForUpdate(hash(token))
			.filter(found -> !found.isExpired(LocalDateTime.now(clock)))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		refreshTokenRepository.delete(saved);
		User user = saved.getUser();
		return new RotatedRefreshToken(user.getId(), issue(user));
	}

	public void revoke(String token) {
		refreshTokenRepository.deleteByTokenHash(hash(token));
	}

	public void revokeAll(Long userId) {
		refreshTokenRepository.deleteAllByUserId(userId);
	}

	public static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			// 자바 표준 구현은 SHA-256을 꼭 제공한다
			throw new IllegalStateException(e);
		}
	}

	public record RotatedRefreshToken(Long userId, String refreshToken) {
	}

}
