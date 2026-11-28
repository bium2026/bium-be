package com.bium.domain.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.recipe.entity.CookingLog;
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.recipe.entity.RecipeType;
import com.bium.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipeListControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private User member;
	private String memberToken;

	@BeforeEach
	void setUp() {
		member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
	}

	@Test
	void 추천받은_레시피와_만들고_싶은_음식_레시피를_최신순으로_돌려준다() throws Exception {
		Recipe recommended = recipe(fridge, "두부조림", RecipeType.RECOMMEND);
		ingredient(recommended, "두부", IngredientSource.FRIDGE);
		ingredient(recommended, "간장", IngredientSource.BASIC);
		Recipe target = recipe(fridge, "김치찌개", RecipeType.TARGET);
		ingredient(target, "돼지고기", IngredientSource.MISSING);
		recipe(em.merge(new Fridge("남의 냉장고")), "남의 레시피", RecipeType.RECOMMEND);

		getRecipes(memberToken, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipes.length()").value(2))
			.andExpect(jsonPath("$.data.recipes[0].id").value(target.getId()))
			.andExpect(jsonPath("$.data.recipes[0].type").value("TARGET"))
			.andExpect(jsonPath("$.data.recipes[0].name").value("김치찌개"))
			.andExpect(jsonPath("$.data.recipes[0].ingredients.length()").value(1))
			.andExpect(jsonPath("$.data.recipes[0].ingredients[0].source").value("MISSING"))
			.andExpect(jsonPath("$.data.recipes[1].id").value(recommended.getId()))
			.andExpect(jsonPath("$.data.recipes[1].type").value("RECOMMEND"))
			.andExpect(jsonPath("$.data.recipes[1].ingredients.length()").value(2))
			.andExpect(jsonPath("$.data.recipes[1].fridgeMatches[0]").value("두부"))
			.andExpect(jsonPath("$.data.nextCursor").value(Matchers.nullValue()));
	}

	@Test
	void 한_번에_12개씩_주고_cursor로_다음_레시피를_받는다() throws Exception {
		for (int i = 1; i <= 13; i++) {
			recipe(fridge, "레시피 " + i, RecipeType.RECOMMEND);
		}

		String firstPage = getRecipes(memberToken, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipes.length()").value(12))
			.andExpect(jsonPath("$.data.recipes[0].name").value("레시피 13"))
			.andExpect(jsonPath("$.data.recipes[11].name").value("레시피 2"))
			.andExpect(jsonPath("$.data.nextCursor").isNumber())
			.andReturn().getResponse().getContentAsString();
		Number nextCursor = JsonPath.read(firstPage, "$.data.nextCursor");

		getRecipes(memberToken, nextCursor.longValue())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipes.length()").value(1))
			.andExpect(jsonPath("$.data.recipes[0].name").value("레시피 1"))
			.andExpect(jsonPath("$.data.nextCursor").value(Matchers.nullValue()));
	}

	@Test
	void 레시피를_지우면_목록과_상세와_요리_후보에서_사라지고_요리_기록은_남는다() throws Exception {
		Recipe recipe = recipe(fridge, "두부조림", RecipeType.RECOMMEND);
		ingredient(recipe, "두부", IngredientSource.FRIDGE);
		CookingLog cookingLog = new CookingLog(fridge, recipe, member);
		em.persist(cookingLog);
		em.flush();

		deleteRecipe(recipe.getId(), memberToken)
			.andExpect(status().isNoContent());
		em.flush();
		em.clear();

		getRecipes(memberToken, null)
			.andExpect(jsonPath("$.data.recipes.length()").value(0));
		mockMvc.perform(get("/api/recipes/{recipeId}", recipe.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
		mockMvc.perform(get("/api/recipes/{recipeId}/cooking-candidates", recipe.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isNotFound());
		deleteRecipe(recipe.getId(), memberToken)
			.andExpect(status().isNotFound());

		assertThat(em.find(Recipe.class, recipe.getId()).isDeleted()).isTrue();
		assertThat(em.find(CookingLog.class, cookingLog.getId())).isNotNull();
	}

	@Test
	void 멤버가_아니면_목록을_보거나_레시피를_지울_수_없다() throws Exception {
		Recipe recipe = recipe(fridge, "두부조림", RecipeType.RECOMMEND);
		User stranger = em.merge(new User("stranger", "password", "남"));
		String strangerToken = jwtProvider.createAccessToken(stranger.getId());

		getRecipes(strangerToken, null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		deleteRecipe(recipe.getId(), strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
		em.flush();
		em.clear();
		assertThat(em.find(Recipe.class, recipe.getId()).isDeleted()).isFalse();
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/recipes", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/recipes/{recipeId}", 1L))
			.andExpect(status().isUnauthorized());
	}

	private Recipe recipe(Fridge target, String title, RecipeType type) {
		Recipe recipe = Recipe.builder().fridge(target).requestedBy(member).type(type).title(title).build();
		em.persist(recipe);
		return recipe;
	}

	private void ingredient(Recipe recipe, String name, IngredientSource source) {
		em.persist(RecipeIngredient.builder().recipe(recipe).name(name).amount("1개").source(source)
			.isRequired(true).build());
	}

	private ResultActions getRecipes(String token, Long cursor) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/fridges/{fridgeId}/recipes", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (cursor != null) {
			request.param("cursor", String.valueOf(cursor));
		}
		return mockMvc.perform(request);
	}

	private ResultActions deleteRecipe(Long recipeId, String token) throws Exception {
		return mockMvc.perform(delete("/api/recipes/{recipeId}", recipeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
