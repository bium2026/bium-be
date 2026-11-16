package com.bium.domain.feed.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.feed.dto.FeedReactionResponse;
import com.bium.domain.feed.entity.FeedId;
import com.bium.domain.feed.entity.FeedLike;
import com.bium.domain.feed.entity.FeedSave;
import com.bium.domain.feed.repository.FeedLikeRepository;
import com.bium.domain.feed.repository.FeedSaveRepository;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 피드 냉장고 좋아요·저장. 같은 요청을 여러 번 보내도 결과가 같다.
 * 공개하지 않은 냉장고에는 누를 수 없다.
 * 넣기는 따로 트랜잭션을 열어, 같은 사용자가 동시에 두 번 보내 기본 키에 걸려도 그 트랜잭션만 되돌리고 넘어간다.
 */
@Service
@RequiredArgsConstructor
public class FeedReactionService {

	private final FeedService feedService;
	private final FeedLikeRepository feedLikeRepository;
	private final FeedSaveRepository feedSaveRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final TransactionTemplate transactionTemplate;

	public FeedReactionResponse like(Long userId, Long fridgeId) {
		feedService.findPublicFridge(fridgeId);
		FeedId id = new FeedId(userId, fridgeId);
		insertOnce(() -> {
			if (!feedLikeRepository.existsById(id)) {
				feedLikeRepository.save(new FeedLike(userReference(userId), fridgeReference(fridgeId)));
			}
		});
		return reaction(userId, fridgeId);
	}

	public FeedReactionResponse unlike(Long userId, Long fridgeId) {
		feedService.findPublicFridge(fridgeId);
		transactionTemplate.executeWithoutResult(status -> feedLikeRepository.deleteByUserIdAndFridgeId(userId, fridgeId));
		return reaction(userId, fridgeId);
	}

	public FeedReactionResponse save(Long userId, Long fridgeId) {
		feedService.findPublicFridge(fridgeId);
		FeedId id = new FeedId(userId, fridgeId);
		insertOnce(() -> {
			if (!feedSaveRepository.existsById(id)) {
				feedSaveRepository.save(new FeedSave(userReference(userId), fridgeReference(fridgeId)));
			}
		});
		return reaction(userId, fridgeId);
	}

	public FeedReactionResponse unsave(Long userId, Long fridgeId) {
		feedService.findPublicFridge(fridgeId);
		transactionTemplate.executeWithoutResult(status -> feedSaveRepository.deleteByUserIdAndFridgeId(userId, fridgeId));
		return reaction(userId, fridgeId);
	}

	// 두 요청이 모두 없다고 보고 넣으면 늦은 쪽이 (user_id, fridge_id) 기본 키에 걸린다. 이미 들어가 있어 결과가 같으므로 넘어간다
	private void insertOnce(Runnable insert) {
		try {
			transactionTemplate.executeWithoutResult(status -> insert.run());
		} catch (DataIntegrityViolationException e) {
			// 먼저 들어간 좋아요·저장이 있다
		}
	}

	private FeedReactionResponse reaction(Long userId, Long fridgeId) {
		FeedId id = new FeedId(userId, fridgeId);
		return new FeedReactionResponse(feedLikeRepository.countByFridgeId(fridgeId), feedLikeRepository.existsById(id),
			feedSaveRepository.existsById(id));
	}

	private User userReference(Long userId) {
		return userRepository.getReferenceById(userId);
	}

	private Fridge fridgeReference(Long fridgeId) {
		return fridgeRepository.getReferenceById(fridgeId);
	}

}
