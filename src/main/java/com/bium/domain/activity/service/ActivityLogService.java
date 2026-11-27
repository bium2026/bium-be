package com.bium.domain.activity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.dto.ActivityLogPageResponse;
import com.bium.domain.activity.dto.ActivityLogResponse;
import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;
import com.bium.domain.activity.repository.ActivityLogRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.recipe.entity.CookingLog;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 공유 냉장고에서 누가 무엇을 했는지 남기고 보여 준다.
 * 기록은 그 동작과 같은 트랜잭션에서 남겨서, 동작이 실패해 되돌려지면 기록도 남지 않는다.
 * 그래서 기록 메서드는 이미 열린 트랜잭션 안에서만 부를 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityLogService {

	static final int PAGE_SIZE = 50;

	private final ActivityLogRepository activityLogRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;

	// 재료가 지워지거나 이름이 바뀐 뒤에도 그때 이름이 보이도록 이름을 함께 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordItem(Long userId, FridgeItem item, ActivityAction action) {
		activityLogRepository.save(ActivityLog.builder()
			.fridge(item.getFridge())
			.user(userRepository.getReferenceById(userId))
			.fridgeItem(item)
			.itemName(item.getName())
			.action(action)
			.build());
	}

	// 요리로 재료를 줄이거나 되돌린 기록을 어느 요리인지와 함께 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordCooking(Long userId, FridgeItem item, CookingLog cookingLog, ActivityAction action) {
		activityLogRepository.save(ActivityLog.builder()
			.fridge(item.getFridge())
			.user(userRepository.getReferenceById(userId))
			.fridgeItem(item)
			.cookingLog(cookingLog)
			.itemName(item.getName())
			.action(action)
			.build());
	}

	// 참여·나가기처럼 재료와 상관없는 기록을 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordMember(Long userId, Long fridgeId, ActivityAction action) {
		activityLogRepository.save(ActivityLog.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.user(userRepository.getReferenceById(userId))
			.action(action)
			.build());
	}

	// 주인이 멤버를 내보낸 기록을 주인이 행동한 것으로 남기고, 내보내진 사람은 targetUser에 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordRemoval(Long ownerId, Long fridgeId, Long targetUserId) {
		recordOwnerAction(ownerId, fridgeId, targetUserId, ActivityAction.REMOVE);
	}

	// 주인 권한을 넘긴 기록을 넘긴 주인이 행동한 것으로 남기고, 새 주인은 targetUser에 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordOwnerTransfer(Long ownerId, Long fridgeId, Long newOwnerId) {
		recordOwnerAction(ownerId, fridgeId, newOwnerId, ActivityAction.TRANSFER_OWNER);
	}

	private void recordOwnerAction(Long ownerId, Long fridgeId, Long targetUserId, ActivityAction action) {
		activityLogRepository.save(ActivityLog.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.user(userRepository.getReferenceById(ownerId))
			.targetUser(userRepository.getReferenceById(targetUserId))
			.action(action)
			.build());
	}

	// cursor가 없으면 최근 기록부터, 있으면 그 기록 다음(더 오래된 기록)부터 준다
	@Transactional(readOnly = true)
	public ActivityLogPageResponse getPage(Long userId, Long fridgeId, Long cursor) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		// 한 개를 더 읽어 다음 페이지가 있는지 본다
		List<ActivityLog> logs = cursor == null
			? activityLogRepository.findRecentWithUserByFridgeId(fridgeId, PageRequest.of(0, PAGE_SIZE + 1))
			: findBefore(fridgeId, findCursor(fridgeId, cursor), PAGE_SIZE + 1);

		List<ActivityLog> shown = new ArrayList<>(logs.subList(0, Math.min(logs.size(), PAGE_SIZE)));
		ActivityLog next = logs.size() > PAGE_SIZE ? logs.get(PAGE_SIZE) : null;
		// 요리 하나로 남긴 기록은 화면에서 한 줄로 묶는다. 페이지 사이에서 나뉘면 앞 페이지에 재료 일부만 보이므로,
		// 마지막 기록과 같은 요리·같은 동작인 기록은 이 페이지에 이어 붙인다
		while (next != null && isSameCooking(shown.get(shown.size() - 1), next)) {
			shown.add(next);
			next = findBefore(fridgeId, next, 1).stream().findFirst().orElse(null);
		}
		Long nextCursor = next == null ? null : shown.get(shown.size() - 1).getId();
		return new ActivityLogPageResponse(shown.stream().map(ActivityLogResponse::from).toList(), nextCursor);
	}

	// 다른 냉장고의 기록 id를 cursor로 보내면 그 냉장고의 기록 순서를 알 수 있으므로 받지 않는다
	private ActivityLog findCursor(Long fridgeId, Long cursor) {
		return activityLogRepository.findById(cursor)
			.filter(log -> log.getFridge().getId().equals(fridgeId))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
	}

	private List<ActivityLog> findBefore(Long fridgeId, ActivityLog log, int size) {
		return activityLogRepository.findWithUserByFridgeIdBefore(fridgeId, log.getCreatedAt(), log.getId(),
			PageRequest.of(0, size));
	}

	private boolean isSameCooking(ActivityLog first, ActivityLog second) {
		return first.getCookingLog() != null && second.getCookingLog() != null
			&& Objects.equals(first.getCookingLog().getId(), second.getCookingLog().getId())
			&& first.getAction() == second.getAction();
	}

}
