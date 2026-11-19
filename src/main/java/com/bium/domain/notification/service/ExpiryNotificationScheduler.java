package com.bium.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 시각이 지난 사용자에게 보낼 유통기한 알림이 있는지 주기적으로 확인한다.
 * 테스트에서는 notification.scheduler-enabled=false로 끄고 서비스를 직접 부른다.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "notification.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExpiryNotificationScheduler {

	private final ExpiryNotificationService notificationService;
	private final Clock clock;

	@Scheduled(cron = "${notification.check-cron}", zone = "Asia/Seoul")
	public void sendDue() {
		int sent = notificationService.sendDue(LocalDateTime.now(clock));
		if (sent > 0) {
			log.info("유통기한 알림 메일 {}통을 보냈습니다.", sent);
		}
	}

}
