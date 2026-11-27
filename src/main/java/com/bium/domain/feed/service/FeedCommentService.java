package com.bium.domain.feed.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.feed.dto.FeedCommentCreateRequest;
import com.bium.domain.feed.dto.FeedCommentPageResponse;
import com.bium.domain.feed.dto.FeedCommentResponse;
import com.bium.domain.feed.entity.FeedComment;
import com.bium.domain.feed.repository.FeedCommentRepository;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 피드 냉장고를 구경한 사람이 남기는 코멘트와 음식 추천.
 * 코멘트는 공개한 냉장고에만 쓸 수 있고, 보기는 피드 냉장고를 볼 수 있는 사람(공개 냉장고는 누구나, 아니면 멤버)만 한다.
 * 지우기는 쓴 사람이나 냉장고 주인만 할 수 있다. 쓴 사람은 냉장고가 비공개로 바뀐 뒤에도 자기 코멘트를 지울 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedCommentService {

	static final int PAGE_SIZE = 20;

	private final FeedService feedService;
	private final FeedCommentRepository feedCommentRepository;
	private final FridgeMemberRepository fridgeMemberRepository;
	private final UserRepository userRepository;

	@Transactional
	public FeedCommentResponse create(Long userId, Long fridgeId, FeedCommentCreateRequest request) {
		Fridge fridge = feedService.findPublicFridge(fridgeId);
		FeedComment comment = feedCommentRepository.save(new FeedComment(fridge, userRepository.getReferenceById(userId),
			request.content(), request.dishName()));
		return FeedCommentResponse.of(comment, userId, isOwner(fridgeId, userId));
	}

	// cursor가 없으면 최신 코멘트부터, 있으면 그 코멘트 다음부터 준다
	public FeedCommentPageResponse getComments(Long userId, Long fridgeId, Long cursor) {
		feedService.findViewableFridge(userId, fridgeId);
		long before = cursor == null ? Long.MAX_VALUE : cursor;
		// 한 개를 더 읽어 다음 페이지가 있는지 본다
		List<FeedComment> comments = feedCommentRepository.findWithUserByFridgeIdBefore(fridgeId, before,
			PageRequest.of(0, PAGE_SIZE + 1));

		boolean hasNext = comments.size() > PAGE_SIZE;
		List<FeedComment> shown = hasNext ? comments.subList(0, PAGE_SIZE) : comments;
		boolean owner = isOwner(fridgeId, userId);
		List<FeedCommentResponse> responses = shown.stream()
			.map(comment -> FeedCommentResponse.of(comment, userId, owner))
			.toList();
		Long nextCursor = hasNext ? shown.get(shown.size() - 1).getId() : null;
		return new FeedCommentPageResponse(responses, nextCursor);
	}

	@Transactional
	public void delete(Long userId, Long fridgeId, Long commentId) {
		Optional<FeedComment> found = feedCommentRepository.findByIdAndFridgeId(commentId, fridgeId);
		if (found.isPresent() && found.get().getUser().getId().equals(userId)) {
			feedCommentRepository.delete(found.get());
			return;
		}
		// 자기 코멘트가 아니면 냉장고를 볼 수 있는지 먼저 확인해, 볼 수 없는 냉장고의 코멘트가 있는지 알 수 없게 한다
		feedService.findViewableFridge(userId, fridgeId);
		FeedComment comment = found.orElseThrow(() -> new BusinessException(ErrorCode.FEED_COMMENT_NOT_FOUND));
		if (!isOwner(fridgeId, userId)) {
			throw new BusinessException(ErrorCode.FEED_COMMENT_DELETE_FORBIDDEN);
		}
		feedCommentRepository.delete(comment);
	}

	private boolean isOwner(Long fridgeId, Long userId) {
		return fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
			.map(member -> member.getRole() == MemberRole.OWNER)
			.orElse(false);
	}

}
