package com.bium.domain.recipe.recommendation;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class RecipePromptBuilder {

	// 프롬프트가 지나치게 길어지지 않게 유통기한이 가까운 재료부터 이 개수만 넣는다
	public static final int MAX_PROMPT_INGREDIENTS = 30;

	private static final String PROMPT_TEMPLATE = """
		너는 1인 가구를 위한 냉장고 재료 기반 레시피 추천 AI야.
		아래 냉장고 재료만 최대한 활용해서 한국어 레시피 후보 6개를 만들어줘.

		조건:
		- 아래 목록에는 유통기한이 지난 재료가 제외되어 있어. 목록에 없는 재료는 사용자가 가진 재료로 취급하지 마.
		- %s
		- 아래 냉장고 재료 목록에 없는 식재료를 필수 재료로 만들지 마.
		- 소금, 후추, 식용유, 물, 간장 같은 기본 양념만 예외로 사용할 수 있어.
		- ingredients에서 required가 true인 항목은 반드시 아래 냉장고 재료 목록 또는 기본 양념이어야 해.
		- fridgeMatches에는 아래 냉장고 재료 목록에 실제로 있는 재료명만 넣어.
		- 이상하거나 실험적인 조합은 만들지 마. 실제 자취생이 바로 먹을 만한 평범한 집밥 메뉴만 추천해.
		- 메뉴 유형 목록에 맞추려고 하지 말고, 냉장고 재료에 자연스럽게 어울리는 실제 집밥으로 제안해.
		- 후보 레시피들은 서로 다른 주재료 조합, 맛의 방향, 식감, 조리 흐름이 느껴지게 구성해.
		- 같은 이름, 같은 핵심 재료 조합, 비슷한 설명을 반복하지 마.
		- 스무디, 주스, 디저트, 케이크, 아이스크림, 잼, 칵테일, 퓨전 요리는 만들지 마.
		- 과일은 단독 요리 재료로 억지 사용하지 말고, 어울릴 때만 샐러드나 토스트의 보조 재료로 써.
		- 요리명은 "두부 계란 볶음", "시금치 덮밥"처럼 자연스러운 실제 음식 이름으로 작성해.
		- difficulty는 EASY, NORMAL, HARD 중 하나로 써.
		- cookTimeMinutes, servings, caloriesKcal, nutrition 값은 단위 없이 숫자로 써.
		- 응답은 JSON만 반환해. 마크다운 코드블록은 쓰지 마.
		- recipes 배열 안에 정확히 6개의 후보 레시피를 넣어. 앱에서는 이 중 다양성이 높은 4개를 보여줄 거야.

		냉장고 재료:
		%s

		JSON 형식:
		{
		  "recipes": [
		    {
		      "name": "요리명",
		      "cookTimeMinutes": 15,
		      "difficulty": "EASY",
		      "servings": 1,
		      "caloriesKcal": 350,
		      "emoji": "🍳",
		      "summary": "한 문장 설명",
		      "ingredients": [
		        { "name": "두부", "amount": "1/2모", "required": true }
		      ],
		      "steps": ["조리 단계 1", "조리 단계 2", "조리 단계 3"],
		      "tips": ["팁 1", "팁 2"],
		      "nutrition": { "proteinG": 20, "carbohydrateG": 45, "fatG": 12 },
		      "fridgeMatches": ["두부", "계란"]
		    }
		  ]
		}
		""";

	// ingredients는 유통기한이 가까운 순서여야 한다
	public String build(List<RecommendationIngredient> ingredients, LocalDate today, boolean prioritizeUrgent) {
		String urgentCondition = prioritizeUrgent
			? "D-Day가 낮은 임박 재료를 우선 사용해."
			: "임박 재료 우선 조건은 약하게 반영해.";
		String ingredientLines = ingredients.stream()
			.limit(MAX_PROMPT_INGREDIENTS)
			.map(ingredient -> "- %s (%s) / %s".formatted(
				ingredient.name(),
				ingredient.emoji() == null ? "" : ingredient.emoji(),
				ingredient.daysLeft(today) == null ? "유통기한 없음" : "D-" + ingredient.daysLeft(today)))
			.collect(Collectors.joining("\n"));

		return PROMPT_TEMPLATE.formatted(
			urgentCondition,
			ingredientLines.isEmpty() ? "- 사용 가능한 재료 없음" : ingredientLines).strip();
	}

}
