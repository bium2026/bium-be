package com.bium.domain.shopping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.shopping.entity.ShoppingListItem;
import com.bium.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShoppingListItemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private User owner;
	private User member;
	private User stranger;
	private String ownerToken;
	private String memberToken;
	private String strangerToken;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "윤호"));
		member = em.merge(new User("member", "password", "선재"));
		stranger = em.merge(new User("stranger", "password", "강희"));
		fridge = em.merge(new Fridge("윤호의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		ownerToken = jwtProvider.createAccessToken(owner.getId());
		memberToken = jwtProvider.createAccessToken(member.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 안_산_재료를_먼저_최근에_담은_순으로_보여_주고_레시피에서_담은_재료는_레시피_이름을_준다() throws Exception {
		Recipe recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
		em.persist(recipe);
		ShoppingListItem tofu = item(fridge, "두부", recipe);
		tofu.check(true);
		ShoppingListItem greenOnion = item(fridge, "대파", recipe);
		ShoppingListItem milk = item(fridge, "우유", null);
		ShoppingListItem egg = item(fridge, "계란", null);
		egg.check(true);
		// 레시피 이름을 목록 조회에서 함께 불러오는지 보려고 미리 불러온 엔티티를 비운다
		em.flush();
		em.clear();

		getItems(ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items.length()").value(4))
			.andExpect(jsonPath("$.data.items[0].id").value(milk.getId()))
			.andExpect(jsonPath("$.data.items[0].name").value("우유"))
			.andExpect(jsonPath("$.data.items[0].checked").value(false))
			.andExpect(jsonPath("$.data.items[0].recipeId").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].recipeName").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.data.items[1].id").value(greenOnion.getId()))
			.andExpect(jsonPath("$.data.items[1].recipeId").value(recipe.getId()))
			.andExpect(jsonPath("$.data.items[1].recipeName").value("두부조림"))
			.andExpect(jsonPath("$.data.items[2].id").value(egg.getId()))
			.andExpect(jsonPath("$.data.items[2].checked").value(true))
			.andExpect(jsonPath("$.data.items[3].id").value(tofu.getId()))
			.andExpect(jsonPath("$.data.items[3].recipeName").value("두부조림"));
	}

	@Test
	void 같은_냉장고의_다른_멤버가_담은_재료도_같은_목록으로_보이고_다른_냉장고의_재료는_보이지_않는다() throws Exception {
		Fridge others = em.merge(new Fridge("남의 냉장고"));
		em.persist(new FridgeMember(others, stranger, MemberRole.OWNER));
		item(others, "남의 우유", null);

		Integer itemId = JsonPath.read(add(memberToken, "{\"name\": \"두부\"}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.data.id");

		getItems(ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(itemId))
			.andExpect(jsonPath("$.data.items[0].name").value("두부"));
		getItems(memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(itemId));
	}

	@Test
	void 직접_담으면_앞뒤_공백을_빼고_담은_사람을_남긴다() throws Exception {
		String response = add(memberToken, "{\"name\": \"  두부  \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.name").value("두부"))
			.andExpect(jsonPath("$.data.checked").value(false))
			.andExpect(jsonPath("$.data.recipeId").doesNotExist())
			.andExpect(jsonPath("$.data.recipeName").doesNotExist())
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString();

		Integer itemId = JsonPath.read(response, "$.data.id");
		ShoppingListItem saved = reload(itemId.longValue());
		assertThat(saved.getName()).isEqualTo("두부");
		assertThat(saved.getFridge().getId()).isEqualTo(fridge.getId());
		assertThat(saved.getCreatedBy().getId()).isEqualTo(member.getId());
		assertThat(saved.getRecipe()).isNull();
		assertThat(saved.getCatalog()).isNull();
	}

	@Test
	void 같은_이름을_다시_담아도_따로_담는다() throws Exception {
		add(memberToken, "{\"name\": \"두부\"}").andExpect(status().isCreated());
		add(ownerToken, "{\"name\": \"두부\"}").andExpect(status().isCreated());

		getItems(memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(2));
	}

	@Test
	void 앞뒤_공백을_뺀_이름이_50자면_담는다() throws Exception {
		String name = "가".repeat(50);

		add(memberToken, "{\"name\": \"  " + name + "  \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value(name));
	}

	@Test
	void 이름이_비었거나_50자를_넘으면_400을_반환한다() throws Exception {
		add(memberToken, "{\"name\": \"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("재료 이름을 입력해 주세요."));
		add(memberToken, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		add(memberToken, "{\"name\": \"" + "가".repeat(51) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("재료 이름은 50자까지 쓸 수 있습니다."));
		// 앞뒤의 줄바꿈·탭도 지우지 않고 막는다
		add(memberToken, "{\"name\": \"\\n두부\\t\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다."));
		add(memberToken, "{\"name\": \"두부\\n우유\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다."));
		add(memberToken, "{\"name\": \"\u200B\"}")
			.andExpect(status().isBadRequest());

		assertThat(countItems()).isZero();
	}

	@Test
	void 샀다고_체크했다가_다시_풀_수_있고_같은_값을_두_번_보내도_결과가_같다() throws Exception {
		ShoppingListItem tofu = item(fridge, "두부", null);

		check(tofu.getId(), memberToken, "{\"checked\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(tofu.getId()))
			.andExpect(jsonPath("$.data.checked").value(true));
		check(tofu.getId(), ownerToken, "{\"checked\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.checked").value(true));
		assertThat(reload(tofu.getId()).isChecked()).isTrue();

		check(tofu.getId(), memberToken, "{\"checked\": false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.checked").value(false));
		assertThat(reload(tofu.getId()).isChecked()).isFalse();
	}

	@Test
	void 레시피에서_담은_재료를_체크해도_레시피_이름을_준다() throws Exception {
		Recipe recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
		em.persist(recipe);
		ShoppingListItem tofu = item(fridge, "두부", recipe);
		em.flush();
		em.clear();

		check(tofu.getId(), memberToken, "{\"checked\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipeId").value(recipe.getId()))
			.andExpect(jsonPath("$.data.recipeName").value("두부조림"));
	}

	@Test
	void 체크_여부를_보내지_않으면_400을_반환한다() throws Exception {
		ShoppingListItem tofu = item(fridge, "두부", null);

		check(tofu.getId(), memberToken, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("샀는지 여부를 보내 주세요."));
	}

	@Test
	void 빼면_204를_반환하고_목록에서_사라진다() throws Exception {
		ShoppingListItem tofu = item(fridge, "두부", null);

		remove(tofu.getId(), ownerToken)
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		assertThat(reload(tofu.getId())).isNull();
		getItems(memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(0));
		remove(tofu.getId(), ownerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SHOPPING_LIST_ITEM_NOT_FOUND"));
	}

	@Test
	void 멤버가_아니면_목록을_보거나_담을_수_없다() throws Exception {
		getItems(strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		add(strangerToken, "{\"name\": \"두부\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));

		assertThat(countItems()).isZero();
	}

	@Test
	void 다른_냉장고의_재료는_체크하거나_뺄_수_없다() throws Exception {
		ShoppingListItem tofu = item(fridge, "두부", null);

		check(tofu.getId(), strangerToken, "{\"checked\": true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SHOPPING_LIST_ITEM_NOT_FOUND"));
		remove(tofu.getId(), strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SHOPPING_LIST_ITEM_NOT_FOUND"));

		ShoppingListItem unchanged = reload(tofu.getId());
		assertThat(unchanged).isNotNull();
		assertThat(unchanged.isChecked()).isFalse();
	}

	@Test
	void 없는_재료는_체크하거나_뺄_수_없다() throws Exception {
		check(Long.MAX_VALUE, memberToken, "{\"checked\": true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SHOPPING_LIST_ITEM_NOT_FOUND"));
		remove(Long.MAX_VALUE, memberToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SHOPPING_LIST_ITEM_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		ShoppingListItem tofu = item(fridge, "두부", null);

		mockMvc.perform(get("/api/fridges/{fridgeId}/shopping-list-items", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/fridges/{fridgeId}/shopping-list-items", fridge.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"두부\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/shopping-list-items/{itemId}", tofu.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"checked\": true}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/shopping-list-items/{itemId}", tofu.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ShoppingListItem item(Fridge target, String name, Recipe recipe) {
		ShoppingListItem item = ShoppingListItem.builder()
			.fridge(target)
			.createdBy(member)
			.recipe(recipe)
			.name(name)
			.build();
		em.persist(item);
		return item;
	}

	private ShoppingListItem reload(Long itemId) {
		em.flush();
		em.clear();
		return em.find(ShoppingListItem.class, itemId);
	}

	private long countItems() {
		return em.createQuery("select count(s) from ShoppingListItem s", Long.class).getSingleResult();
	}

	private ResultActions getItems(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/shopping-list-items", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions add(String token, String body) throws Exception {
		return mockMvc.perform(post("/api/fridges/{fridgeId}/shopping-list-items", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions check(Long itemId, String token, String body) throws Exception {
		return mockMvc.perform(patch("/api/shopping-list-items/{itemId}", itemId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions remove(Long itemId, String token) throws Exception {
		return mockMvc.perform(delete("/api/shopping-list-items/{itemId}", itemId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
