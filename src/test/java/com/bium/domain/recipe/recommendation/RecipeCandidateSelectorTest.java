package com.bium.domain.recipe.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecipeCandidateSelectorTest {

	private static final RecommendationIngredient TOFU = ingredient(1L, "두부");
	private static final RecommendationIngredient EGG = ingredient(2L, "계란");
	private static final List<RecommendationIngredient> FRIDGE = List.of(
		TOFU, EGG, ingredient(3L, "시금치"), ingredient(4L, "닭가슴살"), ingredient(5L, "양파"));

	private final RecipeCandidateSelector selector = new RecipeCandidateSelector();

	@Test
	void 냉장고에_없는_재료를_필수로_쓴_레시피는_뺀다() {
		RecipeCandidate usesBeef = candidate("소고기 덮밥", List.of(
			required("소고기"), required("양파")), List.of("양파"));
		RecipeCandidate usesTofu = candidate("두부 조림", List.of(
			required("두부"), required("간장")), List.of("두부"));

		List<RecommendedRecipe> recipes = selector.select(List.of(usesBeef, usesTofu), FRIDGE);

		assertThat(recipes).extracting(RecommendedRecipe::name).containsExactly("두부 조림");
	}

	@Test
	void 냉장고_재료와_기본_양념만_남기고_출처를_표시한다() {
		RecipeCandidate recipe = candidate("두부 조림", List.of(
			required("두부"), required("간장"), optional("대파")), List.of("두부", "대파"));

		RecommendedRecipe result = selector.select(List.of(recipe), FRIDGE).get(0);

		assertThat(result.ingredients())
			.extracting(RecommendedRecipe.Ingredient::name, RecommendedRecipe.Ingredient::source)
			.containsExactly(
				tuple("두부", IngredientSource.FRIDGE),
				tuple("간장", IngredientSource.BASIC));
		assertThat(result.fridgeMatches()).containsExactly("두부");
	}

	@Test
	void 디저트나_음료_같은_메뉴는_뺀다() {
		RecipeCandidate smoothie = candidate("시금치 스무디", List.of(required("시금치")), List.of("시금치"));
		RecipeCandidate soup = candidate("시금치 된장국", List.of(required("시금치"), required("된장")),
			List.of("시금치"));

		List<RecommendedRecipe> recipes = selector.select(List.of(smoothie, soup), FRIDGE);

		assertThat(recipes).extracting(RecommendedRecipe::name).containsExactly("시금치 된장국");
	}

	@Test
	void 조리_단계가_없으면_뺀다() {
		RecipeCandidate noSteps = new RecipeCandidate("두부 부침", null, null, null, null, null, null,
			List.of(required("두부")), List.of(), List.of(), null, null, null, List.of("두부"));

		assertThat(selector.select(List.of(noSteps), FRIDGE)).isEmpty();
	}

	@Test
	void 비슷한_레시피를_빼고_네_개를_고른다() {
		List<RecipeCandidate> candidates = List.of(
			candidate("두부 계란 볶음", List.of(required("두부"), required("계란")), List.of("두부", "계란")),
			candidate("두부 계란 볶음밥", List.of(required("두부"), required("계란"), required("밥")),
				List.of("두부", "계란")),
			candidate("계란 두부 부침", List.of(required("계란"), required("두부")), List.of("계란", "두부")),
			candidate("시금치 된장국", List.of(required("시금치"), required("된장")), List.of("시금치")),
			candidate("닭가슴살 양파 볶음", List.of(required("닭가슴살"), required("양파")), List.of("닭가슴살", "양파")),
			candidate("양파 계란국", List.of(required("양파"), required("계란")), List.of("양파", "계란")));

		List<RecommendedRecipe> recipes = selector.select(candidates, FRIDGE);

		assertThat(recipes).extracting(RecommendedRecipe::name)
			.containsExactly("두부 계란 볶음", "시금치 된장국", "닭가슴살 양파 볶음", "양파 계란국");
	}

	@Test
	void 다른_레시피가_모자라면_비슷한_레시피로_채운다() {
		List<RecipeCandidate> candidates = List.of(
			candidate("두부 계란 볶음", List.of(required("두부"), required("계란")), List.of("두부", "계란")),
			candidate("두부 계란 볶음밥", List.of(required("두부"), required("계란")), List.of("두부", "계란")),
			candidate("시금치 된장국", List.of(required("시금치"), required("된장")), List.of("시금치")));

		List<RecommendedRecipe> recipes = selector.select(candidates, FRIDGE);

		assertThat(recipes).extracting(RecommendedRecipe::name)
			.containsExactly("두부 계란 볶음", "시금치 된장국", "두부 계란 볶음밥");
	}

	@Test
	void 값이_없으면_기본값을_채운다() {
		RecipeCandidate recipe = new RecipeCandidate("두부 조림", null, null, null, null, 0, null,
			List.of(new RecipeCandidate.Ingredient("두부", null, true)), List.of("두부를 조린다"), List.of(),
			null, null, null, List.of());

		RecommendedRecipe result = selector.select(List.of(recipe), FRIDGE).get(0);

		assertThat(result.emoji()).isEqualTo("🍽️");
		assertThat(result.cookTimeMinutes()).isEqualTo(15);
		assertThat(result.difficulty()).isEqualTo(RecipeDifficulty.EASY);
		assertThat(result.servings()).isEqualTo(1);
		assertThat(result.ingredients().get(0).amount()).isEqualTo("적당량");
		assertThat(result.tips()).hasSize(1);
		assertThat(result.fridgeMatches()).containsExactly("두부");
	}

	@Test
	void AI가_준_재료_이름을_냉장고_재료와_연결한다() {
		RecipeCandidate recipe = candidate("두부 계란 볶음", List.of(required("두부"), required("계란")),
			List.of("계란", "두부", "두부"));

		RecommendedRecipe result = selector.select(List.of(recipe), FRIDGE).get(0);

		assertThat(result.usedIngredients()).containsExactly(EGG, TOFU);
	}

	private static RecommendationIngredient ingredient(Long id, String name) {
		return new RecommendationIngredient(id, name, "🥚", LocalDate.of(2026, 10, 20));
	}

	private RecipeCandidate candidate(String name, List<RecipeCandidate.Ingredient> ingredients,
		List<String> fridgeMatches) {
		return new RecipeCandidate(name, "🍳", name + " 설명", 15, RecipeDifficulty.EASY, 1, 400,
			ingredients, List.of("재료를 손질한다", "조리한다"), List.of("팁"), 20, 40, 10, fridgeMatches);
	}

	private RecipeCandidate.Ingredient required(String name) {
		return new RecipeCandidate.Ingredient(name, "적당량", true);
	}

	private RecipeCandidate.Ingredient optional(String name) {
		return new RecipeCandidate.Ingredient(name, "적당량", false);
	}

}
