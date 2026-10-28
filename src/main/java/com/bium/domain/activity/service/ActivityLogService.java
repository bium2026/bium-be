package com.bium.domain.activity.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.dto.ActivityLogResponse;
import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;
import com.bium.domain.activity.repository.ActivityLogRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.fridge.service.FridgeAccessValidator;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.user.repository.UserRepository;

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

	private static final int RECENT_LIMIT = 50;

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

	// 참여·나가기처럼 재료와 상관없는 기록을 남긴다
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordMember(Long userId, Long fridgeId, ActivityAction action) {
		activityLogRepository.save(ActivityLog.builder()
			.fridge(fridgeRepository.getReferenceById(fridgeId))
			.user(userRepository.getReferenceById(userId))
			.action(action)
			.build());
	}

	@Transactional(readOnly = true)
	public List<ActivityLogResponse> getRecent(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		return activityLogRepository.findRecentWithUserByFridgeId(fridgeId, PageRequest.of(0, RECENT_LIMIT)).stream()
			.map(ActivityLogResponse::from)
			.toList();
	}

}
