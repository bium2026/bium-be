package com.bium.domain.fridge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

	private final FridgeRepository fridgeRepository;
	private final FridgeMemberRepository fridgeMemberRepository;

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

}
