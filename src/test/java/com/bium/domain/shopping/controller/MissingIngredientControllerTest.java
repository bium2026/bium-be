package com.bium.domain.shopping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.recipe.entity.RecipeType;
import com.bium.domain.shopping.entity.ShoppingListItem;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissingIngredientControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private User owner;
	private User member;
	private String memberToken;
	private Recipe recipe;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "윤호"));
		member = em.merge(new User("member", "password", "선재"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());

		recipe = Recipe.builder().fridge(fridge).requestedBy(member).type(RecipeType.TARGET).title("김치찌개").build();
		em.persist(recipe);
		ingredient("김치", IngredientSource.FRIDGE);
		ingredient("두부", IngredientSource.MISSING);
		ingredient("대파", IngredientSource.MISSING);
		ingredient("간장", IngredientSource.BASIC);
	}

	@Test
	void 고른_부족_재료를_장보기_목록에_담고_새로_담은_재료를_돌려준다() throws Exception {
		add(memberToken, "\"두부\", \"대파\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].id").isNumber())
			.andExpect(jsonPath("$.data.items[0].name").value("두부"))
			.andExpect(jsonPath("$.data.items[0].checked").value(false))
			.andExpect(jsonPath("$.data.items[0].recipeId").value(recipe.getId()))
			.andExpect(jsonPath("$.data.items[0].recipeName").value("김치찌개"))
			.andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.data.items[1].name").value("대파"));

		List<ShoppingListItem> items = shoppingItems();
		assertThat(items).extracting(ShoppingListItem::getName).containsExactly("두부", "대파");
		assertThat(items).allSatisfy(item -> {
			assertThat(item.getFridge().getId()).isEqualTo(fridge.getId());
			assertThat(item.getCreatedBy().getId()).isEqualTo(member.getId());
		});
	}

	@Test
	void 안_산_같은_재료가_있으면_건너뛰고_이미_산_재료는_다시_담는다() throws Exception {
		shoppingItem("두부");
		ShoppingListItem boughtGreenOnion = shoppingItem("대파");
		em.createQuery("update ShoppingListItem set isChecked = true where id = :id")
			.setParameter("id", boughtGreenOnion.getId())
			.executeUpdate();

		add(memberToken, "\"두부\", \"대파\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("대파"));

		assertThat(shoppingItems()).hasSize(3);
	}

	@Test
	void 같은_재료를_두_번_보내도_한_번만_담는다() throws Exception {
		add(memberToken, "\"두부\", \" 두부 \"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	void 부족_재료가_아닌_이름이_섞이면_아무것도_담지_않는다() throws Exception {
		add(memberToken, "\"두부\", \"김치\"")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		add(memberToken, "\"두부\", \"우유\"")
			.andExpect(status().isBadRequest());

		assertThat(shoppingItems()).isEmpty();
	}

	@Test
	void 담을_재료를_고르지_않으면_400을_반환한다() throws Exception {
		add(memberToken, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("장보기 목록에 담을 재료를 골라 주세요."));
		add(memberToken, "\" \"")
			.andExpect(status().isBadRequest());
	}

	@Test
	void 멤버가_아니면_레시피를_찾을_수_없다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "강희"));

		add(jwtProvider.createAccessToken(stranger.getId()), "\"두부\"")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));

		assertThat(shoppingItems()).isEmpty();
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/recipes/{recipeId}/shopping-list-items", recipe.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"names\": [\"두부\"]}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions add(String token, String names) throws Exception {
		return mockMvc.perform(post("/api/recipes/{recipeId}/shopping-list-items", recipe.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"names\": [" + names + "]}"));
	}

	private void ingredient(String name, IngredientSource source) {
		em.persist(RecipeIngredient.builder().recipe(recipe).name(name).amount("적당량").source(source).build());
	}

	private ShoppingListItem shoppingItem(String name) {
		ShoppingListItem item = ShoppingListItem.builder().fridge(fridge).createdBy(owner).name(name).build();
		em.persist(item);
		return item;
	}

	private List<ShoppingListItem> shoppingItems() {
		em.flush();
		em.clear();
		return em.createQuery("select s from ShoppingListItem s order by s.id", ShoppingListItem.class).getResultList();
	}

}
