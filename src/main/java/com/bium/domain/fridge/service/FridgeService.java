package com.bium.domain.fridge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.dto.FridgeVisibilityRequest;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

	private final FridgeRepository fridgeRepository;
	private final FridgeMemberRepository fridgeMemberRepository;
	private final FridgeAccessValidator fridgeAccessValidator;

	// 가입하자마자 재료를 넣을 수 있게 그 사용자가 소유자인 냉장고를 하나 만든다
	@Transactional
	public void createDefaultFridge(User owner) {
		Fridge fridge = fridgeRepository.save(new Fridge(owner.getNickname() + "의 냉장고"));
		fridgeMemberRepository.save(new FridgeMember(fridge, owner, MemberRole.OWNER));
	}

	// 내가 소유자이거나 멤버인 냉장고만 돌려준다
	public List<FridgeResponse> getMyFridges(Long userId) {
		return fridgeMemberRepository.findAllWithFridgeByUserId(userId).stream()
			.map(FridgeResponse::from)
			.toList();
	}

	// 냉장고 안 재료가 다른 사용자에게 보이므로 공개 여부는 소유자만 바꾼다
	@Transactional
	public FridgeResponse changeVisibility(Long userId, Long fridgeId, FridgeVisibilityRequest request) {
		fridgeAccessValidator.validateOwner(fridgeId, userId);
		Fridge fridge = fridgeRepository.findById(fridgeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		fridge.changeVisibility(request.isPublic());
		return new FridgeResponse(fridge.getId(), fridge.getName(), MemberRole.OWNER, fridge.isPublic());
	}

}
