package com.bium.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	// 같은 토큰으로 동시에 갱신해도 하나만 새 토큰을 받게 잠그며 읽는다. 나중 요청은 지워진 뒤라 찾지 못한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

	// 이미 지운 토큰으로 로그아웃해도 오류 없이 넘어가게 조건으로 지운다
	@Modifying
	@Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
	int deleteByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from RefreshToken t where t.user.id = :userId")
	int deleteAllByUserId(Long userId);

	// 로그아웃하지 않고 만료된 토큰이 쌓이지 않게, 새 토큰을 줄 때 그 사람의 만료된 토큰을 지운다
	@Modifying
	@Query("delete from RefreshToken t where t.user.id = :userId and t.expiresAt <= :now")
	int deleteExpiredByUserId(Long userId, LocalDateTime now);

}
