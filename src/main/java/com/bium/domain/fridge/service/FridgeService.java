package com.bium.domain.fridge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.repository.FridgeMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

	private final FridgeMemberRepository fridgeMemberRepository;

	// 내가 소유자이거나 멤버인 냉장고만 돌려준다
	public List<FridgeResponse> getMyFridges(Long userId) {
		return fridgeMemberRepository.findAllWithFridgeByUserId(userId).stream()
			.map(FridgeResponse::from)
			.toList();
	}

}
