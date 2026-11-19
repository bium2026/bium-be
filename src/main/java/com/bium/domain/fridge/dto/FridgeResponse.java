package com.bium.domain.fridge.dto;

import java.time.DayOfWeek;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;

// 냉장고와 그 냉장고에서 내 역할. isPublic은 피드에 공개했는지, shoppingDay는 장보는 요일(없으면 null)이다
public record FridgeResponse(Long id, String name, MemberRole role, boolean isPublic, DayOfWeek shoppingDay) {

	public static FridgeResponse from(FridgeMember member) {
		Fridge fridge = member.getFridge();
		return new FridgeResponse(fridge.getId(), fridge.getName(), member.getRole(), fridge.isPublic(),
			fridge.getShoppingDay());
	}

}
