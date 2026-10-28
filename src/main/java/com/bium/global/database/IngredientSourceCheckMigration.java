package com.bium.global.database;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bium.domain.recipe.entity.IngredientSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 레시피 재료 출처(recipe_ingredients.source)의 CHECK 제약을 지금 IngredientSource 값에 맞춘다.
 * Hibernate는 enum 칸에 "source in ('FRIDGE','BASIC')" 같은 CHECK 제약을 만들지만, ddl-auto: update는 이미 있는 제약을 바꾸지 않는다.
 * 그래서 MISSING을 넣기 전에 만든 DB에서는 MISSING을 저장할 수 없다.
 * 마이그레이션 도구 없이 ddl-auto로 표를 관리하므로, 서버를 켤 때 값이 빠진 제약을 지우고 지금 값으로 다시 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientSourceCheckMigration implements ApplicationRunner {

	private static final String CONSTRAINT_NAME = "recipe_ingredients_source_check";

	// MySQL은 schema()가 지금 DB 이름이고, 테스트용 H2는 지금 스키마 이름이라 둘 다 table_schema와 맞는다
	private static final String FIND_CHECKS = """
		select tc.constraint_name, cc.check_clause
		from information_schema.table_constraints tc
		join information_schema.check_constraints cc
		  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
		where tc.table_schema = schema() and lower(tc.table_name) = 'recipe_ingredients'
		  and tc.constraint_type = 'CHECK'
		""";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		try {
			List<String> outdated = jdbcTemplate.query(FIND_CHECKS,
					(rs, rowNum) -> new CheckConstraint(rs.getString(1), rs.getString(2))).stream()
				.filter(CheckConstraint::isOutdatedSourceCheck)
				.map(CheckConstraint::name)
				.toList();
			if (outdated.isEmpty()) {
				return;
			}
			// MySQL은 8.0.16부터 CHECK 제약을 지키고 그때부터 drop check를 쓸 수 있다
			String drop = isMySql() ? "drop check" : "drop constraint";
			outdated.forEach(name -> jdbcTemplate.execute(
				"alter table recipe_ingredients " + drop + " `" + name.replace("`", "``") + "`"));
			jdbcTemplate.execute("alter table recipe_ingredients add constraint " + CONSTRAINT_NAME
				+ " check (source in (" + quotedValues() + "))");
			log.info("recipe_ingredients.source CHECK 제약을 지금 값으로 다시 만들었습니다. 지운 제약={}", outdated);
		} catch (DataAccessException exception) {
			// 제약을 못 고쳐도 다른 기능은 써야 하므로 서버는 켜고, 손으로 고칠 수 있게 남긴다
			log.error("recipe_ingredients.source CHECK 제약을 고치지 못했습니다. 이 제약을 지우고 source in ({})로 다시 만들어 주세요.",
				quotedValues(), exception);
		}
	}

	private boolean isMySql() {
		return "MySQL".equalsIgnoreCase(jdbcTemplate.execute(
			(ConnectionCallback<String>)connection -> connection.getMetaData().getDatabaseProductName()));
	}

	private static String quotedValues() {
		return Arrays.stream(IngredientSource.values())
			.map(value -> "'" + value.name() + "'")
			.collect(Collectors.joining(", "));
	}

	private record CheckConstraint(String name, String clause) {

		// source 칸의 제약인데 지금 값 중 하나라도 빠졌으면 다시 만든다
		boolean isOutdatedSourceCheck() {
			if (clause == null || !clause.toLowerCase(Locale.ROOT).contains("source")) {
				return false;
			}
			// MySQL은 제약 내용을 _utf8mb4\'FRIDGE\'처럼 따옴표 앞에 \를 붙여 보여 준다
			String unescaped = clause.replace("\\", "");
			return Arrays.stream(IngredientSource.values())
				.anyMatch(value -> !unescaped.contains("'" + value.name() + "'"));
		}

	}

}
