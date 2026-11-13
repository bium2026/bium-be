package com.bium.domain.feed.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.feed.entity.FeedId;
import com.bium.domain.feed.entity.FeedLike;

public interface FeedLikeRepository extends JpaRepository<FeedLike, FeedId> {

	// 피드 목록에서 냉장고마다 쿼리가 나가지 않게 여러 냉장고의 좋아요 수를 한 번에 센다. 좋아요가 없는 냉장고는 빠진다
	@Query("select l.fridge.id as fridgeId, count(l) as total from FeedLike l where l.fridge.id in :fridgeIds"
		+ " group by l.fridge.id")
	List<FridgeIdCount> countByFridgeIdIn(Collection<Long> fridgeIds);

	@Query("select l.fridge.id from FeedLike l where l.user.id = :userId and l.fridge.id in :fridgeIds")
	List<Long> findFridgeIdsByUserIdAndFridgeIdIn(Long userId, Collection<Long> fridgeIds);

}
