package com.bium.domain.recipe.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

class RecipeCandidateParserTest {

	private final RecipeCandidateParser parser = new RecipeCandidateParser(JsonMapper.shared());

	@Test
	void 코드블록으로_감싼_응답도_읽는다() {
		String text = """
			```json
			{"recipes": [{"name": "두부 계란 볶음", "cookTimeMinutes": 12, "difficulty": "EASY",
			  "ingredients": [{"name": "두부", "amount": "1/2모", "required": true}],
			  "steps": ["두부를 굽는다"], "fridgeMatches": ["두부"],
			  "nutrition": {"proteinG": 20, "carbohydrateG": 10.6, "fatG": 8}}]}
			```
			""";

		List<RecipeCandidate> candidates = parser.parse(text);

		assertThat(candidates).hasSize(1);
		RecipeCandidate candidate = candidates.get(0);
		assertThat(candidate.name()).isEqualTo("두부 계란 볶음");
		assertThat(candidate.cookTimeMinutes()).isEqualTo(12);
		assertThat(candidate.difficulty()).isEqualTo(RecipeDifficulty.EASY);
		assertThat(candidate.ingredients()).containsExactly(new RecipeCandidate.Ingredient("두부", "1/2모", true));
		assertThat(candidate.carbohydrateG()).isEqualTo(11);
		assertThat(candidate.fridgeMatches()).containsExactly("두부");
	}

	@Test
	void 단위가_붙은_문자열과_한글_난이도도_읽는다() {
		String text = """
			{"recipes": [{"name": "시금치 덮밥", "cookTimeMinutes": "15분", "servings": "1인분",
			  "caloriesKcal": "350kcal", "difficulty": "보통"}]}
			""";

		RecipeCandidate candidate = parser.parse(text).get(0);

		assertThat(candidate.cookTimeMinutes()).isEqualTo(15);
		assertThat(candidate.servings()).isEqualTo(1);
		assertThat(candidate.caloriesKcal()).isEqualTo(350);
		assertThat(candidate.difficulty()).isEqualTo(RecipeDifficulty.NORMAL);
		assertThat(candidate.ingredients()).isEmpty();
		assertThat(candidate.steps()).isEmpty();
	}

	@Test
	void 범위를_벗어난_숫자는_버리고_쉼표가_있는_숫자는_읽는다() {
		String text = """
			{"recipes": [{"name": "두부 조림", "cookTimeMinutes": 1000000000000, "caloriesKcal": "1,200kcal",
			  "nutrition": {"proteinG": -5},
			  "ingredients": [{"name": "두부", "amount": "1모", "required": "true"}]}]}
			""";

		RecipeCandidate candidate = parser.parse(text).get(0);

		assertThat(candidate.cookTimeMinutes()).isNull();
		assertThat(candidate.caloriesKcal()).isEqualTo(1200);
		assertThat(candidate.proteinG()).isNull();
		assertThat(candidate.ingredients().get(0).required()).isTrue();
	}

	@Test
	void recipes가_배열이_아니면_빈_목록을_돌려준다() {
		assertThat(parser.parse("{\"recipes\": {}}")).isEmpty();
	}

	@Test
	void JSON이_아니면_AI_요청_실패로_처리한다() {
		assertThatThrownBy(() -> parser.parse("추천할 수 없습니다"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.AI_REQUEST_FAILED);
	}

	@Test
	void 깨진_JSON은_AI_요청_실패로_처리한다() {
		assertThatThrownBy(() -> parser.parse("{\"recipes\": [}"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.AI_REQUEST_FAILED);
	}

}
