package com.bium.domain.fridge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.fridge.entity.FridgeMember;

public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {

	boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);

	// 목록에서 냉장고 이름을 꺼낼 때 냉장고마다 쿼리가 나가지 않게 함께 불러온다
	@Query("select m from FridgeMember m join fetch m.fridge where m.user.id = :userId order by m.id")
	List<FridgeMember> findAllWithFridgeByUserId(Long userId);

}
