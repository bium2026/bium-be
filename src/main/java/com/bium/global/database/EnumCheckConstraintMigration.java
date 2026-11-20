package com.bium.global.database;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.recipe.entity.IngredientSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * enum 칸의 CHECK 제약을 지금 enum 값에 맞춘다.
 * Hibernate는 enum 칸에 "source in ('FRIDGE','BASIC')" 같은 CHECK 제약을 만들지만, ddl-auto: update는 이미 있는 제약을 바꾸지 않는다.
 * 그래서 enum에 값을 더하기 전에 만든 DB에서는 새 값을 저장할 수 없다.
 * 마이그레이션 도구 없이 ddl-auto로 표를 관리하므로, 서버를 켤 때 값이 빠진 제약을 지우고 지금 값으로 다시 만든다.
 * enum에 값을 더하면 TARGETS에 그 칸이 있어야 하고, 없으면 한 줄 더한다.
 * 시세 채우기처럼 바깥 API를 기다리는 시작 작업보다 먼저 돌아야 그동안 새 값 저장이 막히지 않는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class EnumCheckConstraintMigration implements ApplicationRunner {

	private static final List<Target> TARGETS = List.of(
		new Target("recipe_ingredients", "source", IngredientSource.class),
		new Target("activity_logs", "action", ActivityAction.class));

	// MySQL은 schema()가 지금 DB 이름이고, 테스트용 H2는 지금 스키마 이름이라 둘 다 table_schema와 맞는다
	private static final String FIND_CHECKS = """
		select tc.constraint_name, cc.check_clause
		from information_schema.table_constraints tc
		join information_schema.check_constraints cc
		  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
		where tc.table_schema = schema() and lower(tc.table_name) = ?
		  and tc.constraint_type = 'CHECK'
		""";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		// 한 칸을 못 고쳐도 다른 칸은 고쳐야 하므로 칸마다 따로 처리한다
		TARGETS.forEach(this::migrate);
	}

	private void migrate(Target target) {
		try {
			List<String> outdated = jdbcTemplate.query(FIND_CHECKS,
					(rs, rowNum) -> new CheckConstraint(rs.getString(1), rs.getString(2)), target.table()).stream()
				.filter(check -> check.isOutdatedCheckOf(target))
				.map(CheckConstraint::name)
				.toList();
			if (outdated.isEmpty()) {
				return;
			}
			String add = "add constraint " + target.constraintName() + " check (" + target.column() + " in ("
				+ target.quotedValues() + "))";
			if (isMySql()) {
				// 지우기와 만들기를 한 문장으로 해서, 만들기가 실패하면 지운 제약도 그대로 남게 한다.
				// MySQL은 8.0.16부터 CHECK 제약을 지키고 그때부터 drop check를 쓸 수 있다
				String drops = outdated.stream()
					.map(name -> "drop check " + quoteName(name))
					.collect(Collectors.joining(", "));
				jdbcTemplate.execute("alter table " + target.table() + " " + drops + ", " + add);
			} else {
				// 테스트용 H2는 alter table 한 문장에 여러 작업을 쓸 수 없어 나눠서 한다
				outdated.forEach(name ->
					jdbcTemplate.execute("alter table " + target.table() + " drop constraint " + quoteName(name)));
				jdbcTemplate.execute("alter table " + target.table() + " " + add);
			}
			log.info("{}.{} CHECK 제약을 지금 값으로 다시 만들었습니다. 지운 제약={}", target.table(), target.column(), outdated);
		} catch (DataAccessException exception) {
			// 제약을 못 고쳐도 다른 기능은 써야 하므로 서버는 켜고, 손으로 고칠 수 있게 남긴다
			log.error("{}.{} CHECK 제약을 고치지 못했습니다. 이 제약을 지우고 {} in ({})로 다시 만들어 주세요.",
				target.table(), target.column(), target.column(), target.quotedValues(), exception);
		}
	}

	private boolean isMySql() {
		return "MySQL".equalsIgnoreCase(jdbcTemplate.execute(
			(ConnectionCallback<String>)connection -> connection.getMetaData().getDatabaseProductName()));
	}

	private static String quoteName(String name) {
		return "`" + name.replace("`", "``") + "`";
	}

	private record Target(String table, String column, Class<? extends Enum<?>> type) {

		String constraintName() {
			return table + "_" + column + "_check";
		}

		String quotedValues() {
			return Arrays.stream(type.getEnumConstants())
				.map(value -> "'" + value.name() + "'")
				.collect(Collectors.joining(", "));
		}

	}

	private record CheckConstraint(String name, String clause) {

		// 그 칸의 제약인데 지금 값 중 하나라도 빠졌으면 다시 만든다
		boolean isOutdatedCheckOf(Target target) {
			// 다른 칸 이름의 일부(예: source_id)와 헷갈리지 않게 식별자 경계로 칸 이름을 찾는다
			Pattern column = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(target.column()) + "(?![a-z0-9_])");
			if (clause == null || !column.matcher(clause.toLowerCase(Locale.ROOT)).find()) {
				return false;
			}
			// MySQL은 제약 내용을 _utf8mb4\'FRIDGE\'처럼 따옴표 앞에 \를 붙여 보여 준다
			String unescaped = clause.replace("\\", "");
			return Arrays.stream(target.type().getEnumConstants())
				.anyMatch(value -> !unescaped.contains("'" + value.name() + "'"));
		}

	}

}
