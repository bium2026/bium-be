package com.bium.domain.fridge.dto;

import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;

// 냉장고와 그 냉장고에서 내 역할
public record FridgeResponse(Long id, String name, MemberRole role) {

	public static FridgeResponse from(FridgeMember member) {
		return new FridgeResponse(member.getFridge().getId(), member.getFridge().getName(), member.getRole());
	}

}
