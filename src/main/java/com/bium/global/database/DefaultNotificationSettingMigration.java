package com.bium.global.database;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bium.domain.notification.service.NotificationSettingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 설정 줄이 없는 사용자에게 기본 설정(켜짐, 로그인 이메일, 9시, D-3·D-day)을 만든다.
 * 예전에는 가입할 때 설정 줄을 만들지 않고 화면에만 기본값을 보여 줘서, 한 번도 저장하지 않은 사용자에게는 메일이 가지 않았다.
 * 지금은 가입할 때 만들고, 그 전에 가입한 사용자는 서버를 켤 때 여기서 채운다. 줄이 있는 사용자는 건드리지 않는다.
 * 예전 users.email로 받을 주소를 채우는 LoginEmailMigration 다음에 돈다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class DefaultNotificationSettingMigration implements ApplicationRunner {

	private static final String INSERT_MISSING_SETTINGS = """
		insert into notification_settings (user_id, email, enabled, notify_time, notify_d3, notify_dday)
		select u.id, u.email, true, ?, true, true from users u
		where not exists (select 1 from notification_settings s where s.user_id = u.id)
		""";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int created = jdbcTemplate.update(INSERT_MISSING_SETTINGS, NotificationSettingService.DEFAULT_NOTIFY_TIME);
			if (created > 0) {
				log.info("알림 설정이 없던 사용자 {}명에게 기본 알림 설정을 만들었습니다.", created);
			}
		} catch (DataAccessException exception) {
			// 못 채워도 다른 기능은 써야 하므로 서버는 켜고, 다음에 켤 때 다시 한다
			log.error("기본 알림 설정을 만들지 못했습니다. 설정을 저장한 적이 없는 사용자에게는 알림 메일이 가지 않습니다.", exception);
		}
	}

}
