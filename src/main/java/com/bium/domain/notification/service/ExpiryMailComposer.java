package com.bium.domain.notification.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bium.domain.ingredient.entity.FridgeItem;

/**
 * 유통기한 알림 메일의 제목과 본문을 만든다. 메일 앱마다 HTML이 다르게 보여 글자만 보낸다.
 */
@Component
public class ExpiryMailComposer {

	private final String appUrl;

	public ExpiryMailComposer(@Value("${notification.app-url}") String appUrl) {
		this.appUrl = appUrl;
	}

	public String subject(int itemCount) {
		return "[비움] 유통기한이 다가온 재료 " + itemCount + "개";
	}

	public String testSubject() {
		return "[비움] 알림 메일 테스트";
	}

	// items는 유통기한이 오늘 또는 3일 뒤인 재료다
	public String body(String nickname, List<FridgeItem> items, LocalDate today) {
		StringBuilder body = new StringBuilder();
		body.append(nickname).append(" 님, 냉장고에 유통기한이 다가온 재료가 있어요.\n");
		appendSection(body, "오늘까지 먹어야 해요 (D-day)", items, today);
		appendSection(body, "3일 남았어요 (D-3)", items, today.plusDays(3));
		body.append("\n비움에서 확인하기: ").append(appUrl).append("/fridge\n");
		body.append("알림은 비움의 알림 설정에서 끄거나 시각을 바꿀 수 있어요.\n");
		return body.toString();
	}

	public String testBody(String nickname, List<FridgeItem> items, LocalDate today) {
		if (items.isEmpty()) {
			return nickname + " 님, 비움 알림 메일이 잘 도착했어요.\n"
				+ "지금은 유통기한이 오늘이거나 3일 남은 재료가 없어요. 그런 재료가 생기면 정한 시각에 알려 드릴게요.\n";
		}
		return "비움 알림 메일이 잘 도착했어요. 지금 기준으로는 이렇게 보내요.\n\n" + body(nickname, items, today);
	}

	private void appendSection(StringBuilder body, String title, List<FridgeItem> items, LocalDate date) {
		List<FridgeItem> onDate = items.stream().filter(item -> date.equals(item.getExpiresAt())).toList();
		if (onDate.isEmpty()) {
			return;
		}
		body.append("\n").append(title).append("\n");
		onDate.forEach(item -> body.append("- ")
			.append(item.getEmoji() == null ? "" : item.getEmoji() + " ")
			.append(item.getName())
			.append(" (").append(item.getFridge().getName()).append(")\n"));
	}

}
