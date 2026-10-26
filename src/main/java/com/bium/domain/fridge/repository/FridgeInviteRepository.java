package com.bium.domain.fridge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.fridge.entity.FridgeInvite;

public interface FridgeInviteRepository extends JpaRepository<FridgeInvite, Long> {

	boolean existsByCode(String code);

	Optional<FridgeInvite> findByCode(String code);

	// 만료 여부는 호출하는 쪽에서 지금 시각으로 따진다
	@Query("select i from FridgeInvite i where i.fridge.id = :fridgeId and i.isActive = true order by i.id")
	List<FridgeInvite> findAllActiveByFridgeId(Long fridgeId);

}
