package com.bium.domain.fridge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.fridge.entity.FridgeInvite;

import jakarta.persistence.LockModeType;

public interface FridgeInviteRepository extends JpaRepository<FridgeInvite, Long> {

	boolean existsByCode(String code);

	Optional<FridgeInvite> findByCode(String code);

	// 만료 여부는 호출하는 쪽에서 지금 시각으로 따진다
	@Query("select i from FridgeInvite i where i.fridge.id = :fridgeId and i.isActive = true order by i.id")
	List<FridgeInvite> findAllActiveByFridgeId(Long fridgeId);

	// 코드를 막을 때 쓴다. MySQL REPEATABLE READ에서 일반 조회는 트랜잭션의 첫 조회 시점 스냅샷을 읽어
	// 그 뒤 다른 요청이 커밋한 코드를 놓치므로, 잠그며 읽어 지금 커밋된 코드를 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from FridgeInvite i where i.fridge.id = :fridgeId and i.isActive = true order by i.id")
	List<FridgeInvite> findAllActiveByFridgeIdForUpdate(Long fridgeId);

}
