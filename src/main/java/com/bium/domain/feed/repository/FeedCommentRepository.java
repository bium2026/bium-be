package com.bium.domain.feed.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.feed.entity.FeedComment;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

	// 코멘트마다 닉네임을 꺼낼 때 쿼리가 따로 나가지 않게 작성자를 함께 불러온다. (fridge_id, id) 인덱스 순서대로 읽는다
	@Query("select c from FeedComment c join fetch c.user where c.fridge.id = :fridgeId and c.id < :cursor"
		+ " order by c.id desc")
	List<FeedComment> findWithUserByFridgeIdBefore(Long fridgeId, Long cursor, Pageable pageable);

	Optional<FeedComment> findByIdAndFridgeId(Long id, Long fridgeId);

	// 피드 목록에서 냉장고마다 쿼리가 나가지 않게 여러 냉장고의 코멘트 수를 한 번에 센다. 코멘트가 없는 냉장고는 빠진다
	@Query("select c.fridge.id as fridgeId, count(c) as total from FeedComment c where c.fridge.id in :fridgeIds"
		+ " group by c.fridge.id")
	List<FridgeIdCount> countByFridgeIdIn(Collection<Long> fridgeIds);

}
