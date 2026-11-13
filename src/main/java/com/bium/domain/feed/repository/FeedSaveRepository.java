package com.bium.domain.feed.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.feed.entity.FeedId;
import com.bium.domain.feed.entity.FeedSave;
import com.bium.domain.fridge.entity.Fridge;

public interface FeedSaveRepository extends JpaRepository<FeedSave, FeedId> {

	// 저장한 냉장고 중 지금도 공개된 냉장고만, 전체 피드와 같은 순서와 cursor로 보여 준다
	@Query("select s.fridge from FeedSave s where s.user.id = :userId and s.fridge.isPublic = true"
		+ " and s.fridge.id < :cursor order by s.fridge.id desc")
	List<Fridge> findSavedPublicBefore(Long userId, Long cursor, Pageable pageable);

	@Query("select s.fridge.id from FeedSave s where s.user.id = :userId and s.fridge.id in :fridgeIds")
	List<Long> findFridgeIdsByUserIdAndFridgeIdIn(Long userId, Collection<Long> fridgeIds);

}
