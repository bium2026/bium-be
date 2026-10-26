package com.bium.domain.recipe.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.recipe.entity.CookingLog;

import jakarta.persistence.LockModeType;

public interface CookingLogRepository extends JpaRepository<CookingLog, Long> {

	// 되돌리기를 동시에 두 번 눌러 재료가 두 번 복원되지 않게 기록을 잠그고 읽는다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from CookingLog l where l.id = :id")
	Optional<CookingLog> findByIdForUpdate(Long id);

}
