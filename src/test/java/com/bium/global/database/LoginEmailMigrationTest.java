package com.bium.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 표 구조를 바꾸는 테스트라 테스트 트랜잭션 없이 돌리고, 끝나면 넣은 행을 지운다.
 */
@SpringBootTest
class LoginEmailMigrationTest {

	private static final String COUNT_LOGIN_ID_COLUMN = """
		select count(*) from information_schema.columns
		where table_schema = schema() and lower(table_name) = 'users' and lower(column_name) = 'login_id'
		""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private LoginEmailMigration migration;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		userIds.forEach(id -> {
			jdbcTemplate.update("delete from notification_settings where user_id = ?", id);
			jdbcTemplate.update("delete from users where id = ?", id);
		});
		if (hasLoginIdColumn()) {
			jdbcTemplate.execute("alter table users drop column login_id");
		}
		jdbcTemplate.execute("drop index if exists uk_users_email");
	}

	@Test
	void 아이디_로그인_때_만든_DB면_알림_받을_주소를_예전_이메일로_채우고_login_id를_지운다() {
		// 아이디로 로그인하던 때 DB처럼 login_id 칸을 두고, 알림 받을 주소는 users.email에만 있게 한다
		jdbcTemplate.execute("alter table users add column login_id varchar(20)");
		long withEmail = insertUser("old@bium.test", "olduser");
		long withoutEmail = insertUser(null, "noemail");
		insertSetting(withEmail);
		insertSetting(withoutEmail);

		migration.run(null);

		assertThat(hasLoginIdColumn()).isFalse();
		assertThat(settingEmail(withEmail)).isEqualTo("old@bium.test");
		assertThat(settingEmail(withoutEmail)).isNull();
		assertThat(hasIndex("uk_users_email")).isTrue();

		// 다시 켜도 지운 받을 주소를 로그인 이메일로 다시 채우지 않는다
		jdbcTemplate.update("update notification_settings set email = null where user_id = ?", withEmail);
		migration.run(null);
		assertThat(settingEmail(withEmail)).isNull();
	}

	@Test
	void login_id가_없는_DB는_그대로_둔다() {
		long user = insertUser("new@bium.test", null);
		insertSetting(user);

		migration.run(null);

		assertThat(settingEmail(user)).isNull();
	}

	private long insertUser(String email, String loginId) {
		if (loginId == null) {
			jdbcTemplate.update("insert into users (email, password, nickname) values (?, 'password', '강희')", email);
		} else {
			jdbcTemplate.update("insert into users (email, password, nickname, login_id) values (?, 'password', '강희', ?)",
				email, loginId);
		}
		Long id = jdbcTemplate.queryForObject("select max(id) from users", Long.class);
		userIds.add(id);
		return id;
	}

	private void insertSetting(long userId) {
		jdbcTemplate.update("insert into notification_settings (user_id, enabled, notify_time, notify_d3, notify_dday)"
			+ " values (?, true, ?, true, true)", userId, LocalTime.of(9, 0));
	}

	private String settingEmail(long userId) {
		return jdbcTemplate.queryForObject("select email from notification_settings where user_id = ?", String.class, userId);
	}

	private boolean hasIndex(String name) {
		Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.indexes"
			+ " where table_schema = schema() and lower(index_name) = ?", Integer.class, name);
		return count != null && count > 0;
	}

	private boolean hasLoginIdColumn() {
		Integer count = jdbcTemplate.queryForObject(COUNT_LOGIN_ID_COLUMN, Integer.class);
		return count != null && count > 0;
	}

}
