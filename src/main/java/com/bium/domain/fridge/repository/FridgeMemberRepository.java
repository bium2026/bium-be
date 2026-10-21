package com.bium.domain.fridge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.fridge.entity.FridgeMember;

public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {

	boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);

}
