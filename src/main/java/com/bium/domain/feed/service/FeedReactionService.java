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
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 피드 냉장고 좋아요·저장. 같은 요청을 여러 번 보내도 결과가 같다.
 * 공개하지 않은 냉장고와 내가 주인이나 멤버인 냉장고에는 누를 수 없다.
 * 취소는 냉장고가 비공개로 바뀌었거나 누른 뒤 그 냉장고에 참여했어도 할 수 있게 공개 여부와 멤버 여부를 보지 않고 내 기록만 지운다.
 * 멤버가 남긴 좋아요·저장은 그 냉장고에 남아 있어도 좋아요 수와 내 좋아요·저장 여부에 넣지 않는다.
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
	private final FridgeAccessValidator fridgeAccessValidator;
	private final TransactionTemplate transactionTemplate;

	public FeedReactionResponse like(Long userId, Long fridgeId) {
		validateNotMine(userId, fridgeId);
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
		transactionTemplate.executeWithoutResult(status -> feedLikeRepository.deleteByUserIdAndFridgeId(userId, fridgeId));
		return reaction(userId, fridgeId);
	}

	public FeedReactionResponse save(Long userId, Long fridgeId) {
		validateNotMine(userId, fridgeId);
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
		transactionTemplate.executeWithoutResult(status -> feedSaveRepository.deleteByUserIdAndFridgeId(userId, fridgeId));
		return reaction(userId, fridgeId);
	}

	// 멤버는 공개하지 않은 냉장고도 볼 수 있어 있는지 숨길 필요가 없으므로, 공개 여부보다 먼저 보고 막은 이유를 알려 준다
	private void validateNotMine(Long userId, Long fridgeId) {
		if (fridgeAccessValidator.isMember(fridgeId, userId)) {
			throw new BusinessException(ErrorCode.OWN_FRIDGE_REACTION_FORBIDDEN);
		}
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
		boolean mine = fridgeAccessValidator.isMember(fridgeId, userId);
		return new FeedReactionResponse(feedLikeRepository.countByFridgeId(fridgeId),
			!mine && feedLikeRepository.existsById(id), !mine && feedSaveRepository.existsById(id));
	}

	private User userReference(Long userId) {
		return userRepository.getReferenceById(userId);
	}

	private Fridge fridgeReference(Long fridgeId) {
		return fridgeRepository.getReferenceById(fridgeId);
	}

}
