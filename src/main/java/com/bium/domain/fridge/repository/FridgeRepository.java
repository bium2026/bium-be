package com.bium.domain.fridge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

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

	// 피드에는 공개한 냉장고를 최근에 만든 순서로 보여 주고, 마지막으로 받은 냉장고 id(cursor) 다음부터 이어 준다
	@Query("select f from Fridge f where f.isPublic = true and f.id < :cursor order by f.id desc")
	List<Fridge> findPublicBefore(Long cursor, Pageable pageable);

	@Query("select f from Fridge f where f.id = :fridgeId and f.isPublic = true")
	Optional<Fridge> findPublicById(Long fridgeId);

}
