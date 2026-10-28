package com.bium.domain.fridge.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.service.ActivityLogService;
import com.bium.domain.fridge.dto.FridgeMemberResponse;
import com.bium.domain.fridge.entity.FridgeInvite;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeInviteRepository;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고를 함께 쓰는 멤버를 보여 주고, 내보내거나 스스로 나가게 한다.
 * 소유자가 빠지면 초대와 내보내기를 할 사람이 없어지므로 소유자는 나가거나 내보내질 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FridgeMemberService {

	private final FridgeMemberRepository fridgeMemberRepository;
	private final FridgeInviteRepository fridgeInviteRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final ActivityLogService activityLogService;

	// 소유자를 맨 앞에 두고 나머지는 참여한 순서로 보여 준다
	@Transactional(readOnly = true)
	public List<FridgeMemberResponse> getMembers(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateMember(fridgeId, userId);
		return fridgeMemberRepository.findAllWithUserByFridgeId(fridgeId).stream()
			.sorted(Comparator.comparing(member -> member.getRole() != MemberRole.OWNER))
			.map(member -> FridgeMemberResponse.of(member, userId))
			.toList();
	}

	public void remove(Long userId, Long fridgeId, Long targetUserId) {
		fridgeAccessValidator.validateOwner(fridgeId, userId);
		if (userId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
		}
		FridgeMember target = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_MEMBER_NOT_FOUND));
		fridgeMemberRepository.delete(target);
		// 기록에는 행동한 사람이 하나만 들어가므로 내보내진 사람이 나간 것으로 남긴다.
		// 초대 코드 만들기처럼 냉장고 행을 먼저 잡고 초대 코드를 잠가야 둘이 동시에 와도 서로 막히지 않아서 기록을 먼저 넣는다
		activityLogService.recordMember(targetUserId, fridgeId, ActivityAction.LEAVE);
		// 내보낸 사람이 아직 쓸 수 있는 코드로 바로 다시 들어오지 못하게 지금 코드도 막는다
		fridgeInviteRepository.findAllActiveByFridgeIdForUpdate(fridgeId).forEach(FridgeInvite::deactivate);
	}

	public void leave(Long userId, Long fridgeId) {
		FridgeMember me = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		if (me.getRole() == MemberRole.OWNER) {
			throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
		}
		fridgeMemberRepository.delete(me);
		activityLogService.recordMember(userId, fridgeId, ActivityAction.LEAVE);
	}

}
