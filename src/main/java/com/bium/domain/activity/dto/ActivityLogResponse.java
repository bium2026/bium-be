package com.bium.domain.activity.dto;

import java.time.LocalDateTime;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;
import com.bium.domain.recipe.entity.CookingLog;

/**
 * 참여·나가기처럼 재료와 상관없는 기록은 itemName이 null이다.
 * 요리로 재료를 줄이거나 되돌린 기록은 cookingLogId로 같은 요리를 묶을 수 있고, undone이면 이미 되돌린 요리다.
 * 요리와 상관없는 기록은 cookingLogId가 null이고 undone은 false다.
 */
public record ActivityLogResponse(Long id, String nickname, ActivityAction action, String itemName,
	Long cookingLogId, boolean undone, LocalDateTime createdAt) {

	public static ActivityLogResponse from(ActivityLog log) {
		CookingLog cookingLog = log.getCookingLog();
		return new ActivityLogResponse(log.getId(), log.getUser().getNickname(), log.getAction(), log.getItemName(),
			cookingLog == null ? null : cookingLog.getId(), cookingLog != null && cookingLog.isUndone(),
			log.getCreatedAt());
	}

}
