package com.bium.domain.activity.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

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

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.activity.entity.ActivityLog;
import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeInvite;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityLogControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

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
	void 재료를_넣고_고치고_지우면_최근_기록부터_보여_준다() throws Exception {
		IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
			.owner(member)
			.name("두부")
			.defaultStorage(StorageType.FRIDGE)
			.build());
		String created = mockMvc.perform(post("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"catalogId\": " + catalog.getId() + ", \"name\": \"두부\", \"storage\": \"FRIDGE\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Integer itemId = JsonPath.read(created, "$.data.id");

		mockMvc.perform(put("/api/fridge-items/{itemId}", itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"순두부\", \"version\": 0}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/fridge-items/{itemId}", itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
				.param("version", "1"))
			.andExpect(status().isNoContent());

		getActivities(memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].action").value("DELETE"))
			.andExpect(jsonPath("$.data[0].nickname").value("윤호"))
			// 지운 재료도 기록에는 이름이 남는다
			.andExpect(jsonPath("$.data[0].itemName").value("순두부"))
			.andExpect(jsonPath("$.data[0].createdAt").exists())
			.andExpect(jsonPath("$.data[1].action").value("UPDATE"))
			.andExpect(jsonPath("$.data[1].nickname").value("선재"))
			.andExpect(jsonPath("$.data[1].itemName").value("순두부"))
			.andExpect(jsonPath("$.data[2].action").value("ADD"))
			.andExpect(jsonPath("$.data[2].nickname").value("선재"))
			.andExpect(jsonPath("$.data[2].itemName").value("두부"));
	}

	@Test
	void 참여_나가기_내보내기를_기록한다() throws Exception {
		em.persist(new FridgeInvite(fridge, "K7MX2QPA", owner, LocalDateTime.now(clock).plusHours(1)));

		mockMvc.perform(post("/api/fridges/join")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\": \"K7MX2QPA\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/fridges/{fridgeId}/members/me", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/fridges/{fridgeId}/members/{userId}", fridge.getId(), member.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isNoContent());

		getActivities(ownerToken)
			.andExpect(jsonPath("$.data.length()").value(3))
			// 내보내진 사람은 그 사람이 나간 것으로 남는다
			.andExpect(jsonPath("$.data[0].action").value("LEAVE"))
			.andExpect(jsonPath("$.data[0].nickname").value("선재"))
			.andExpect(jsonPath("$.data[0].itemName").doesNotExist())
			.andExpect(jsonPath("$.data[1].action").value("LEAVE"))
			.andExpect(jsonPath("$.data[1].nickname").value("강희"))
			.andExpect(jsonPath("$.data[2].action").value("JOIN"))
			.andExpect(jsonPath("$.data[2].nickname").value("강희"));
	}

	@Test
	void 버전_확인에서_막힌_수정은_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		update(tofu, "{\"name\": \"순두부\", \"version\": 5}")
			.andExpect(status().isConflict());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 바꾼_값_없이_저장하면_수정_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		update(tofu, "{\"name\": \"두부\", \"version\": 0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(0));

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 요리로_뺀_재료와_되돌린_재료를_하나씩_기록한다() throws Exception {
		FridgeItem tofu = persistItem("두부");
		FridgeItem greenOnion = persistItem("대파");
		Recipe recipe = persistRecipe();

		String cooked = cook(recipe, used(tofu, 0) + "," + used(greenOnion, 0))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Integer cookingLogId = JsonPath.read(cooked, "$.data.id");
		mockMvc.perform(post("/api/cooking-logs/{cookingLogId}/undo", cookingLogId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isOk());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.length()").value(4))
			.andExpect(jsonPath("$.data[0].action").value("RESTORE"))
			.andExpect(jsonPath("$.data[0].nickname").value("윤호"))
			.andExpect(jsonPath("$.data[0].itemName").value("대파"))
			.andExpect(jsonPath("$.data[1].action").value("RESTORE"))
			.andExpect(jsonPath("$.data[1].itemName").value("두부"))
			.andExpect(jsonPath("$.data[2].action").value("DEDUCT"))
			.andExpect(jsonPath("$.data[2].nickname").value("선재"))
			.andExpect(jsonPath("$.data[2].itemName").value("대파"))
			.andExpect(jsonPath("$.data[3].action").value("DEDUCT"))
			.andExpect(jsonPath("$.data[3].itemName").value("두부"));
	}

	@Test
	void 버전_확인에서_막힌_요리는_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		cook(persistRecipe(), used(tofu, 5))
			.andExpect(status().isConflict());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 최근_50개까지만_최근_순으로_보여_준다() throws Exception {
		for (int i = 0; i <= 50; i++) {
			em.persist(ActivityLog.builder()
				.fridge(fridge)
				.user(member)
				.itemName("재료" + i)
				.action(ActivityAction.UPDATE)
				.build());
		}

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.length()").value(50))
			.andExpect(jsonPath("$.data[0].itemName").value("재료50"))
			.andExpect(jsonPath("$.data[49].itemName").value("재료1"));
	}

	@Test
	void 다른_냉장고의_기록은_보이지_않고_멤버가_아니면_볼_수_없다() throws Exception {
		Fridge others = em.merge(new Fridge("남의 냉장고"));
		em.persist(new FridgeMember(others, stranger, MemberRole.OWNER));
		em.persist(ActivityLog.builder()
			.fridge(others)
			.user(stranger)
			.itemName("남의 우유")
			.action(ActivityAction.ADD)
			.build());

		getActivities(memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
		getActivities(strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/activities", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getActivities(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/activities", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions update(FridgeItem item, String body) throws Exception {
		return mockMvc.perform(put("/api/fridge-items/{itemId}", item.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions cook(Recipe recipe, String items) throws Exception {
		return mockMvc.perform(post("/api/recipes/{recipeId}/cooking-logs", recipe.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\": [" + items + "]}"));
	}

	private String used(FridgeItem item, int version) {
		return "{\"fridgeItemId\": " + item.getId() + ", \"version\": " + version + "}";
	}

	private Recipe persistRecipe() {
		Recipe recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
		em.persist(recipe);
		return recipe;
	}

	private FridgeItem persistTofu() {
		return persistItem("두부");
	}

	private FridgeItem persistItem(String name) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.build();
		em.persist(item);
		return item;
	}

}
