package com.bium.domain.fridge.dto;

import java.time.LocalDateTime;

import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;

// 화면은 토큰만 들고 있어 내 userId를 모르므로, 내 줄인지는 isMe로 알려 준다
public record FridgeMemberResponse(Long userId, String nickname, MemberRole role, LocalDateTime joinedAt,
	boolean isMe) {

	public static FridgeMemberResponse of(FridgeMember member, Long myUserId) {
		return new FridgeMemberResponse(member.getUser().getId(), member.getUser().getNickname(), member.getRole(),
			member.getJoinedAt(), member.getUser().getId().equals(myUserId));
	}

}
