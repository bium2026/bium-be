package com.bium.domain.activity.dto;

import java.time.LocalDateTime;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;
import com.bium.domain.recipe.entity.CookingLog;

/**
 * 참여·나가기·내보내기처럼 재료와 상관없는 기록은 itemName이 null이다.
 * 요리로 재료를 줄이거나 되돌린 기록은 cookingLogId로 같은 요리를 묶을 수 있고, undone이면 이미 되돌린 요리다.
 * 요리와 상관없는 기록은 cookingLogId가 null이고 undone은 false다.
 * 멤버를 내보낸 기록(REMOVE)만 targetNickname에 내보내진 멤버의 닉네임이 들어가고, 나머지는 null이다.
 */
public record ActivityLogResponse(Long id, String nickname, ActivityAction action, String itemName,
	Long cookingLogId, boolean undone, String targetNickname,
	LocalDateTime createdAt) {

	public static ActivityLogResponse from(ActivityLog log) {
		CookingLog cookingLog = log.getCookingLog();
		return new ActivityLogResponse(log.getId(), log.getUser().getNickname(), log.getAction(), log.getItemName(),
			cookingLog == null ? null : cookingLog.getId(), cookingLog != null && cookingLog.isUndone(),
			log.getTargetUser() == null ? null : log.getTargetUser().getNickname(), log.getCreatedAt());
	}

}
