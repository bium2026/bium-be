package com.bium.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 표 구조를 바꾸는 테스트라 테스트 트랜잭션 없이 돌리고, 끝나면 지금 값을 모두 허용하는 제약이 남는다.
 */
@SpringBootTest
class IngredientSourceCheckMigrationTest {

	private static final String SOURCE_CHECKS = """
		select tc.constraint_name, cc.check_clause
		from information_schema.table_constraints tc
		join information_schema.check_constraints cc
		  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
		where tc.table_schema = schema() and lower(tc.table_name) = 'recipe_ingredients'
		  and tc.constraint_type = 'CHECK' and lower(cc.check_clause) like '%source%'
		""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private IngredientSourceCheckMigration migration;

	@Test
	void MISSING을_넣기_전에_만든_제약을_지금_값으로_다시_만들고_다시_켜도_그대로_둔다() {
		// MISSING을 넣기 전 DB처럼 FRIDGE·BASIC만 허용하는 제약으로 바꿔 둔다
		sourceCheckNames().forEach(name ->
			jdbcTemplate.execute("alter table recipe_ingredients drop constraint `" + name + "`"));
		jdbcTemplate.execute(
			"alter table recipe_ingredients add constraint old_source_check check (source in ('FRIDGE', 'BASIC'))");

		migration.run(null);

		// MySQL은 따옴표 앞에 \를 붙여 보여 줘서 지우고 비교한다
		assertThat(sourceCheckClauses()).singleElement()
			.satisfies(clause -> assertThat(clause.replace("\\", "")).contains("'FRIDGE'", "'BASIC'", "'MISSING'"));
		List<String> namesAfterFirstRun = sourceCheckNames();

		migration.run(null);

		assertThat(sourceCheckNames()).isEqualTo(namesAfterFirstRun);
	}

	private List<String> sourceCheckNames() {
		return jdbcTemplate.query(SOURCE_CHECKS, (rs, rowNum) -> rs.getString(1));
	}

	private List<String> sourceCheckClauses() {
		return jdbcTemplate.query(SOURCE_CHECKS, (rs, rowNum) -> rs.getString(2));
	}

}
