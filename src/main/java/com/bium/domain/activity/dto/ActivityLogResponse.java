package com.bium.domain.activity.dto;

import java.time.LocalDateTime;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;

// 참여·나가기처럼 재료와 상관없는 기록은 itemName이 null이다
public record ActivityLogResponse(Long id, String nickname, ActivityAction action, String itemName,
	LocalDateTime createdAt) {

	public static ActivityLogResponse from(ActivityLog log) {
		return new ActivityLogResponse(log.getId(), log.getUser().getNickname(), log.getAction(), log.getItemName(),
			log.getCreatedAt());
	}

}
