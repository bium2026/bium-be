package com.bium.global.database;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 아이디 로그인에서 이메일 로그인으로 바꾸기 전에 만든 DB를 지금 표에 맞춘다.
 * 예전에는 users.login_id(NOT NULL)로 로그인했고 users.email이 알림을 받을 주소였다.
 * ddl-auto: update는 칸을 지우지 않아, login_id가 남아 있으면 이메일로 가입할 때 login_id에 넣을 값이 없어 실패한다.
 * login_id 칸이 남은 DB에서만 한 번 돌며, 알림 설정의 받을 주소를 예전 users.email로 채운 뒤 login_id를 지우고
 * users.email에 유니크 인덱스를 만든다.
 * 채우기가 실패하면 login_id를 남겨 다음에 켤 때 다시 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LoginEmailMigration implements ApplicationRunner {

	// MySQL은 schema()가 지금 DB 이름이고, 테스트용 H2는 지금 스키마 이름이라 둘 다 table_schema와 맞는다
	private static final String COUNT_LOGIN_ID_COLUMN = """
		select count(*) from information_schema.columns
		where table_schema = schema() and lower(table_name) = 'users' and lower(column_name) = 'login_id'
		""";

	private static final String COPY_NOTIFICATION_EMAIL = """
		update notification_settings s
		set email = (select u.email from users u where u.id = s.user_id)
		where s.email is null
		""";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			Integer count = jdbcTemplate.queryForObject(COUNT_LOGIN_ID_COLUMN, Integer.class);
			if (count == null || count == 0) {
				return;
			}
			int copied = jdbcTemplate.update(COPY_NOTIFICATION_EMAIL);
			jdbcTemplate.execute("alter table users drop column login_id");
			log.info("이메일 로그인에 맞춰 users.login_id를 지웠습니다. 알림 받을 주소를 채운 설정={}", copied);
		} catch (DataAccessException exception) {
			// 못 맞춰도 다른 기능은 써야 하므로 서버는 켜고, 손으로 고칠 수 있게 남긴다
			log.error("users.login_id를 지우지 못했습니다. 이 칸이 남아 있으면 가입이 실패합니다."
				+ " notification_settings.email을 users.email로 채운 뒤 login_id 칸을 지워 주세요.", exception);
			return;
		}
		createEmailUniqueIndex();
	}

	// ddl-auto: update는 이미 있는 칸에 유니크 제약을 더하지 않아 직접 만든다.
	// 예전에 같은 알림 주소를 쓰던 계정이 여럿이면 만들 수 없어, 가입할 때 확인하는 것으로 막고 정리하라고 남긴다
	private void createEmailUniqueIndex() {
		try {
			jdbcTemplate.execute("create unique index uk_users_email on users (email)");
		} catch (DataAccessException exception) {
			log.warn("users.email 유니크 인덱스를 만들지 못했습니다. 같은 이메일 계정이 여럿이면 정리한 뒤"
				+ " 'create unique index uk_users_email on users (email)'로 만들어 주세요.", exception);
		}
	}

}
