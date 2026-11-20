package com.bium.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bium.domain.activity.entity.ActivityAction;

/**
 * 표 구조를 바꾸는 테스트라 테스트 트랜잭션 없이 돌리고, 끝나면 지금 값을 모두 허용하는 제약이 남는다.
 */
@SpringBootTest
class EnumCheckConstraintMigrationTest {

	private static final String COLUMN_CHECKS = """
		select tc.constraint_name, cc.check_clause
		from information_schema.table_constraints tc
		join information_schema.check_constraints cc
		  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
		where tc.table_schema = schema() and lower(tc.table_name) = ?
		  and tc.constraint_type = 'CHECK' and lower(cc.check_clause) like ?
		""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EnumCheckConstraintMigration migration;

	@Test
	void MISSING을_넣기_전에_만든_제약을_지금_값으로_다시_만들고_다시_켜도_그대로_둔다() {
		// MISSING을 넣기 전 DB처럼 FRIDGE·BASIC만 허용하는 제약으로 바꿔 둔다
		replaceCheck("recipe_ingredients", "source", "old_source_check", "'FRIDGE', 'BASIC'");

		migration.run(null);

		// MySQL은 따옴표 앞에 \를 붙여 보여 줘서 지우고 비교한다
		assertThat(checkClauses("recipe_ingredients", "source")).singleElement()
			.satisfies(clause -> assertThat(clause.replace("\\", "")).contains("'FRIDGE'", "'BASIC'", "'MISSING'"));
		List<String> namesAfterFirstRun = checkNames("recipe_ingredients", "source");

		migration.run(null);

		assertThat(checkNames("recipe_ingredients", "source")).isEqualTo(namesAfterFirstRun);
	}

	@Test
	void REMOVE를_넣기_전에_만든_활동_기록_제약을_지금_값으로_다시_만들고_다시_켜도_그대로_둔다() {
		// REMOVE를 넣기 전 DB처럼 옛 값만 허용하는 제약으로 바꿔 둔다
		replaceCheck("activity_logs", "action", "old_action_check",
			"'ADD', 'UPDATE', 'MOVE', 'DELETE', 'DEDUCT', 'RESTORE', 'JOIN', 'LEAVE'");

		migration.run(null);

		// MySQL은 따옴표 앞에 \를 붙여 보여 줘서 지우고 비교한다
		assertThat(checkClauses("activity_logs", "action")).singleElement()
			.satisfies(clause -> assertThat(clause.replace("\\", ""))
				.contains(Arrays.stream(ActivityAction.values()).map(value -> "'" + value.name() + "'").toArray(String[]::new)));
		List<String> namesAfterFirstRun = checkNames("activity_logs", "action");

		migration.run(null);

		assertThat(checkNames("activity_logs", "action")).isEqualTo(namesAfterFirstRun);
	}

	private void replaceCheck(String table, String column, String name, String values) {
		checkNames(table, column).forEach(old ->
			jdbcTemplate.execute("alter table " + table + " drop constraint `" + old + "`"));
		jdbcTemplate.execute("alter table " + table + " add constraint " + name + " check (" + column + " in (" + values + "))");
	}

	private List<String> checkNames(String table, String column) {
		return jdbcTemplate.query(COLUMN_CHECKS, (rs, rowNum) -> rs.getString(1), table, "%" + column + "%");
	}

	private List<String> checkClauses(String table, String column) {
		return jdbcTemplate.query(COLUMN_CHECKS, (rs, rowNum) -> rs.getString(2), table, "%" + column + "%");
	}

}
