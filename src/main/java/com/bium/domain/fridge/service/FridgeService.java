package com.bium.domain.fridge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.dto.FridgeNameRequest;
import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.dto.FridgeShoppingDayRequest;
import com.bium.domain.fridge.dto.FridgeVisibilityRequest;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
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
	private final UserRepository userRepository;

	// 가입하자마자 재료를 넣을 수 있게 그 사용자가 소유자인 냉장고를 하나 만든다
	@Transactional
	public void createDefaultFridge(User owner) {
		Fridge fridge = fridgeRepository.save(new Fridge(owner.getNickname() + "의 냉장고"));
		fridgeMemberRepository.save(new FridgeMember(fridge, owner, MemberRole.OWNER));
	}

	// 공유 냉장고에서 나가거나 내보내져 속한 냉장고가 하나도 없는 사용자가 새 냉장고를 만들고 소유자가 된다.
	// 이미 냉장고가 있으면 가입할 때 만든 냉장고처럼 하나씩만 두도록 더 만들지 않는다
	@Transactional
	public FridgeResponse create(Long userId, FridgeNameRequest request) {
		if (fridgeMemberRepository.existsByUserId(userId)) {
			throw new BusinessException(ErrorCode.FRIDGE_ALREADY_EXISTS);
		}
		Fridge fridge = fridgeRepository.save(new Fridge(request.name().strip()));
		FridgeMember owner = fridgeMemberRepository.save(
			new FridgeMember(fridge, userRepository.getReferenceById(userId), MemberRole.OWNER));
		return FridgeResponse.from(owner);
	}

	// 냉장고 이름은 멤버 모두에게 보이므로 공개 여부처럼 소유자만 바꾼다
	@Transactional
	public FridgeResponse rename(Long userId, Long fridgeId, FridgeNameRequest request) {
		fridgeAccessValidator.validateOwner(fridgeId, userId);
		Fridge fridge = fridgeRepository.findById(fridgeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		fridge.rename(request.name().strip());
		return new FridgeResponse(fridge.getId(), fridge.getName(), MemberRole.OWNER, fridge.isPublic(),
			fridge.getShoppingDay());
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
		return new FridgeResponse(fridge.getId(), fridge.getName(), MemberRole.OWNER, fridge.isPublic(),
			fridge.getShoppingDay());
	}

	// 장보기는 멤버가 함께 하므로 장보는 요일은 멤버 누구나 바꾼다. null이면 요일을 지운다
	@Transactional
	public FridgeResponse changeShoppingDay(Long userId, Long fridgeId, FridgeShoppingDayRequest request) {
		FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		member.getFridge().changeShoppingDay(request.shoppingDay());
		return FridgeResponse.from(member);
	}

}
