package com.bium.domain.fridge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.fridge.entity.FridgeMember;

public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {

	boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);

	Optional<FridgeMember> findByFridgeIdAndUserId(Long fridgeId, Long userId);

	// 목록에서 냉장고 이름을 꺼낼 때 냉장고마다 쿼리가 나가지 않게 함께 불러온다
	@Query("select m from FridgeMember m join fetch m.fridge where m.user.id = :userId order by m.id")
	List<FridgeMember> findAllWithFridgeByUserId(Long userId);

	// 멤버 목록에서 닉네임을 꺼낼 때 멤버마다 쿼리가 나가지 않게 함께 불러온다
	@Query("select m from FridgeMember m join fetch m.user where m.fridge.id = :fridgeId order by m.id")
	List<FridgeMember> findAllWithUserByFridgeId(Long fridgeId);

	// 피드 목록에서 냉장고마다 쿼리가 나가지 않게 여러 냉장고의 멤버를 닉네임과 함께 한 번에 불러온다
	@Query("select m from FridgeMember m join fetch m.user where m.fridge.id in :fridgeIds order by m.id")
	List<FridgeMember> findAllWithUserByFridgeIdIn(Collection<Long> fridgeIds);

}
