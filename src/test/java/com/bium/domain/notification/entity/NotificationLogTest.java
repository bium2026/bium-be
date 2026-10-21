package com.bium.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationLogTest {

	@Test
	void 오류_메시지가_500자를_넘으면_500자로_잘라서_저장한다() {
		NotificationLog log = new NotificationLog(null, 1, NotificationStatus.FAIL, "a".repeat(501));

		assertThat(log.getErrorMessage()).hasSize(500);
	}

	@Test
	void 오류_메시지가_500자_이하이면_그대로_저장한다() {
		String message = "a".repeat(500);

		NotificationLog log = new NotificationLog(null, 1, NotificationStatus.FAIL, message);

		assertThat(log.getErrorMessage()).isEqualTo(message);
	}

	@Test
	void 오류_메시지가_없으면_null로_저장한다() {
		NotificationLog log = new NotificationLog(null, 1, NotificationStatus.SUCCESS, null);

		assertThat(log.getErrorMessage()).isNull();
	}

}
