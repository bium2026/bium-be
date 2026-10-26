package com.bium.domain.fridge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.fridge.entity.Fridge;

import jakarta.persistence.LockModeType;

public interface FridgeRepository extends JpaRepository<Fridge, Long> {

	// 같은 냉장고의 초대 코드를 동시에 새로 만들 때 하나씩 처리해 쓸 수 있는 코드가 하나만 남게 한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Fridge f where f.id = :fridgeId")
	Optional<Fridge> findByIdForUpdate(Long fridgeId);

}
