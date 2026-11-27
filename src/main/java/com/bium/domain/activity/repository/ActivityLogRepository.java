package com.bium.domain.activity.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.activity.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

	// 기록마다 닉네임과 되돌림 여부를 꺼낼 때 쿼리가 따로 나가지 않게 사용자, 내보내진 사용자, 요리 기록을 함께 불러온다.
	// (fridge_id, created_at) 인덱스 순서대로 읽게 created_at으로 정렬하고, 같은 시각은 id로 가른다
	@Query("select a from ActivityLog a join fetch a.user left join fetch a.targetUser "
		+ "left join fetch a.cookingLog where a.fridge.id = :fridgeId order by a.createdAt desc, a.id desc")
	List<ActivityLog> findRecentWithUserByFridgeId(Long fridgeId, Pageable pageable);

	// 위와 같은 순서로, (createdAt, id)가 주어진 기록보다 앞선(더 오래된) 기록만 읽는다
	@Query("select a from ActivityLog a join fetch a.user left join fetch a.targetUser "
		+ "left join fetch a.cookingLog where a.fridge.id = :fridgeId "
		+ "and (a.createdAt < :createdAt or (a.createdAt = :createdAt and a.id < :id)) "
		+ "order by a.createdAt desc, a.id desc")
	List<ActivityLog> findWithUserByFridgeIdBefore(Long fridgeId, LocalDateTime createdAt, Long id, Pageable pageable);

}
