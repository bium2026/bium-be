package com.bium.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 서버를 켤 때 도는 작업이라 테스트 트랜잭션 없이 돌리고, 끝나면 넣은 행을 지운다.
 */
@SpringBootTest
class DefaultNotificationSettingMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DefaultNotificationSettingMigration migration;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		userIds.forEach(id -> {
			jdbcTemplate.update("delete from notification_settings where user_id = ?", id);
			jdbcTemplate.update("delete from users where id = ?", id);
		});
	}

	@Test
	void 설정을_저장한_적이_없는_사용자에게_기본_설정을_만들고_저장한_설정은_그대로_둔다() {
		long neverSaved = insertUser("never@bium.test");
		long turnedOff = insertUser("off@bium.test");
		// 알림을 끄고 받을 주소를 지운 사용자
		jdbcTemplate.update("insert into notification_settings (user_id, enabled, notify_time, notify_d3, notify_dday)"
			+ " values (?, false, ?, true, false)", turnedOff, LocalTime.of(7, 0));

		migration.run(null);
		// 다시 켜도 같은 사용자에게 또 만들지 않는다
		migration.run(null);

		Map<String, Object> created = setting(neverSaved);
		assertThat(created.get("email")).isEqualTo("never@bium.test");
		assertThat(created.get("enabled")).isEqualTo(true);
		assertThat(created.get("notify_time").toString()).startsWith("09:00");
		assertThat(created.get("notify_d3")).isEqualTo(true);
		assertThat(created.get("notify_dday")).isEqualTo(true);
		assertThat(created.get("last_sent_date")).isNull();

		Map<String, Object> kept = setting(turnedOff);
		assertThat(kept.get("email")).isNull();
		assertThat(kept.get("enabled")).isEqualTo(false);
		assertThat(kept.get("notify_dday")).isEqualTo(false);
	}

	private long insertUser(String email) {
		jdbcTemplate.update("insert into users (email, password, nickname) values (?, 'password', '강희')", email);
		Long id = jdbcTemplate.queryForObject("select max(id) from users", Long.class);
		userIds.add(id);
		return id;
	}

	private Map<String, Object> setting(long userId) {
		return jdbcTemplate.queryForMap("select * from notification_settings where user_id = ?", userId);
	}

}
