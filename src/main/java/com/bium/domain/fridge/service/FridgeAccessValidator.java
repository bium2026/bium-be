package com.bium.domain.fridge.service;

import org.springframework.stereotype.Component;

import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 냉장고 멤버만 냉장고와 그 안의 재료·레시피를 다룰 수 있게 막는다.
 */
@Component
@RequiredArgsConstructor
public class FridgeAccessValidator {

	private final FridgeMemberRepository fridgeMemberRepository;

	public boolean isMember(Long fridgeId, Long userId) {
		return fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId);
	}

	// 멤버가 아니면 냉장고가 있는지도 알 수 없게 없는 냉장고와 같은 오류를 준다
	public void validateMember(Long fridgeId, Long userId) {
		if (!isMember(fridgeId, userId)) {
			throw new BusinessException(ErrorCode.FRIDGE_NOT_FOUND);
		}
	}

	// 초대 코드와 멤버 내보내기는 소유자만 한다. 멤버가 아니면 validateMember와 같은 오류를 준다
	public void validateOwner(Long fridgeId, Long userId) {
		FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		if (member.getRole() != MemberRole.OWNER) {
			throw new BusinessException(ErrorCode.FRIDGE_OWNER_ONLY);
		}
	}

}
