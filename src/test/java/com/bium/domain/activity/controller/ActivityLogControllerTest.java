package com.bium.domain.activity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import com.bium.domain.recipe.entity.CookingLog;
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
			.andExpect(jsonPath("$.data.activities.length()").value(3))
			.andExpect(jsonPath("$.data.activities[0].action").value("DELETE"))
			.andExpect(jsonPath("$.data.activities[0].nickname").value("윤호"))
			// 지운 재료도 기록에는 이름이 남는다
			.andExpect(jsonPath("$.data.activities[0].itemName").value("순두부"))
			.andExpect(jsonPath("$.data.activities[0].createdAt").exists())
			.andExpect(jsonPath("$.data.activities[1].action").value("UPDATE"))
			.andExpect(jsonPath("$.data.activities[1].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[1].itemName").value("순두부"))
			.andExpect(jsonPath("$.data.activities[2].action").value("ADD"))
			.andExpect(jsonPath("$.data.activities[2].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[2].itemName").value("두부"));
	}

	@Test
	void 재료를_여러_개_한번에_넣으면_재료마다_기록한다() throws Exception {
		addAll("{\"name\": \"우유\", \"storage\": \"FRIDGE\"}, {\"name\": \"감자\", \"storage\": \"ROOM\"}")
			.andExpect(status().isCreated());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(2))
			.andExpect(jsonPath("$.data.activities[0].action").value("ADD"))
			.andExpect(jsonPath("$.data.activities[0].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[0].itemName").value("감자"))
			.andExpect(jsonPath("$.data.activities[1].action").value("ADD"))
			.andExpect(jsonPath("$.data.activities[1].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[1].itemName").value("우유"))
			.andExpect(jsonPath("$.data.activities[1].targetNickname").value(nullValue()));
	}

	@Test
	void 입력이_잘못된_재료가_섞이면_기록을_남기지_않는다() throws Exception {
		addAll("{\"name\": \"우유\", \"storage\": \"FRIDGE\"}, {\"name\": \" \", \"storage\": \"ROOM\"}")
			.andExpect(status().isBadRequest());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(0));
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

		// 테스트 트랜잭션 안이라 조회가 메모리 엔티티를 돌려주므로, 비워서 DB에서 다시 읽은 값을 검증한다
		em.flush();
		em.clear();
		getActivities(ownerToken)
			.andExpect(jsonPath("$.data.activities.length()").value(3))
			// 내보내기는 내보낸 주인이 행동한 것으로 남고, 내보내진 사람은 targetNickname에만 나온다
			.andExpect(jsonPath("$.data.activities[0].action").value("REMOVE"))
			.andExpect(jsonPath("$.data.activities[0].nickname").value("윤호"))
			.andExpect(jsonPath("$.data.activities[0].targetNickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[0].itemName").doesNotExist())
			.andExpect(jsonPath("$.data.activities[1].action").value("LEAVE"))
			.andExpect(jsonPath("$.data.activities[1].nickname").value("강희"))
			.andExpect(jsonPath("$.data.activities[1].targetNickname").value(nullValue()))
			.andExpect(jsonPath("$.data.activities[2].action").value("JOIN"))
			.andExpect(jsonPath("$.data.activities[2].nickname").value("강희"))
			.andExpect(jsonPath("$.data.activities[2].targetNickname").value(nullValue()));
	}

	@Test
	void 버전_확인에서_막힌_수정은_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		update(tofu, "{\"name\": \"순두부\", \"version\": 5}")
			.andExpect(status().isConflict());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(0));
	}

	@Test
	void 바꾼_값_없이_저장하면_수정_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		update(tofu, "{\"name\": \"두부\", \"version\": 0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(0));

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(0));
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
			.andExpect(jsonPath("$.data.activities.length()").value(4))
			.andExpect(jsonPath("$.data.activities[0].action").value("RESTORE"))
			.andExpect(jsonPath("$.data.activities[0].nickname").value("윤호"))
			.andExpect(jsonPath("$.data.activities[0].itemName").value("대파"))
			.andExpect(jsonPath("$.data.activities[1].action").value("RESTORE"))
			.andExpect(jsonPath("$.data.activities[1].nickname").value("윤호"))
			.andExpect(jsonPath("$.data.activities[1].itemName").value("두부"))
			.andExpect(jsonPath("$.data.activities[2].action").value("DEDUCT"))
			.andExpect(jsonPath("$.data.activities[2].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[2].itemName").value("대파"))
			.andExpect(jsonPath("$.data.activities[3].action").value("DEDUCT"))
			.andExpect(jsonPath("$.data.activities[3].nickname").value("선재"))
			.andExpect(jsonPath("$.data.activities[3].itemName").value("두부"));
	}

	@Test
	void 요리_기록에는_어느_요리인지와_되돌렸는지를_함께_돌려준다() throws Exception {
		FridgeItem tofu = persistItem("두부");
		String cooked = cook(persistRecipe(), used(tofu, 0))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Integer cookingLogId = JsonPath.read(cooked, "$.data.id");

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities[0].cookingLogId").value(cookingLogId))
			.andExpect(jsonPath("$.data.activities[0].undone").value(false));

		mockMvc.perform(post("/api/cooking-logs/{cookingLogId}/undo", cookingLogId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isOk());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities[0].action").value("RESTORE"))
			.andExpect(jsonPath("$.data.activities[0].cookingLogId").value(cookingLogId))
			.andExpect(jsonPath("$.data.activities[0].undone").value(true))
			.andExpect(jsonPath("$.data.activities[1].action").value("DEDUCT"))
			.andExpect(jsonPath("$.data.activities[1].cookingLogId").value(cookingLogId))
			.andExpect(jsonPath("$.data.activities[1].undone").value(true));
	}

	@Test
	void 요리와_상관없는_기록은_요리_정보가_비어_있다() throws Exception {
		FridgeItem tofu = persistItem("두부");
		mockMvc.perform(delete("/api/fridge-items/{itemId}", tofu.getId())
				.param("version", "0")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isNoContent());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities[0].action").value("DELETE"))
			.andExpect(jsonPath("$.data.activities[0].cookingLogId").doesNotExist())
			.andExpect(jsonPath("$.data.activities[0].undone").value(false));
	}

	@Test
	void 같은_재료를_두_번_보내도_차감_기록은_하나만_남긴다() throws Exception {
		FridgeItem tofu = persistTofu();

		cook(persistRecipe(), used(tofu, 0) + "," + used(tofu, 0))
			.andExpect(status().isCreated());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(1))
			.andExpect(jsonPath("$.data.activities[0].action").value("DEDUCT"));
	}

	@Test
	void 버전_확인에서_막힌_요리는_기록을_남기지_않는다() throws Exception {
		FridgeItem tofu = persistTofu();

		cook(persistRecipe(), used(tofu, 5))
			.andExpect(status().isConflict());

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(0));
	}

	@Test
	void 최근_50개씩_최근_순으로_보여_주고_cursor로_다음_페이지를_준다() throws Exception {
		for (int i = 0; i <= 50; i++) {
			persistLog("재료" + i, ActivityAction.UPDATE, null);
		}

		String first = getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(50))
			.andExpect(jsonPath("$.data.activities[0].itemName").value("재료50"))
			.andExpect(jsonPath("$.data.activities[49].itemName").value("재료1"))
			.andReturn().getResponse().getContentAsString();
		Integer lastId = JsonPath.read(first, "$.data.activities[49].id");
		Integer nextCursor = JsonPath.read(first, "$.data.nextCursor");
		assertThat(nextCursor).isEqualTo(lastId);

		getActivities(memberToken, nextCursor)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.activities.length()").value(1))
			.andExpect(jsonPath("$.data.activities[0].itemName").value("재료0"))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	void 기록이_50개_이하면_다음_페이지가_없다() throws Exception {
		persistLog("우유", ActivityAction.ADD, null);

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(1))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	void 요리_하나로_남긴_기록은_페이지_사이에서_나누지_않는다() throws Exception {
		CookingLog cooking = new CookingLog(fridge, persistRecipe(), member);
		em.persist(cooking);
		persistLog("가장 오래된 재료", ActivityAction.UPDATE, null);
		for (int i = 0; i < 3; i++) {
			persistLog("요리 재료" + i, ActivityAction.DEDUCT, cooking);
		}
		for (int i = 0; i < 48; i++) {
			persistLog("재료" + i, ActivityAction.UPDATE, null);
		}

		// 50번째와 51번째가 같은 요리의 기록이라 51개를 주고, 다음 페이지는 그 요리 다음부터 시작한다
		String first = getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(51))
			.andExpect(jsonPath("$.data.activities[48].itemName").value("요리 재료2"))
			.andExpect(jsonPath("$.data.activities[50].itemName").value("요리 재료0"))
			.andReturn().getResponse().getContentAsString();
		Integer nextCursor = JsonPath.read(first, "$.data.nextCursor");

		getActivities(memberToken, nextCursor)
			.andExpect(jsonPath("$.data.activities.length()").value(1))
			.andExpect(jsonPath("$.data.activities[0].itemName").value("가장 오래된 재료"))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	void 요리_기록이_마지막이면_이어_붙인_뒤_다음_페이지가_없다() throws Exception {
		CookingLog cooking = new CookingLog(fridge, persistRecipe(), member);
		em.persist(cooking);
		for (int i = 0; i < 2; i++) {
			persistLog("요리 재료" + i, ActivityAction.DEDUCT, cooking);
		}
		for (int i = 0; i < 49; i++) {
			persistLog("재료" + i, ActivityAction.UPDATE, null);
		}

		getActivities(memberToken)
			.andExpect(jsonPath("$.data.activities.length()").value(51))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	void 다른_냉장고의_기록을_cursor로_보내면_400을_준다() throws Exception {
		Fridge others = em.merge(new Fridge("남의 냉장고"));
		em.persist(new FridgeMember(others, stranger, MemberRole.OWNER));
		ActivityLog othersLog = ActivityLog.builder()
			.fridge(others)
			.user(stranger)
			.itemName("남의 우유")
			.action(ActivityAction.ADD)
			.build();
		em.persist(othersLog);

		getActivities(memberToken, othersLog.getId().intValue())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
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
			.andExpect(jsonPath("$.data.activities.length()").value(0));
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

	private ResultActions getActivities(String token, Integer cursor) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/activities", fridge.getId())
			.param("cursor", String.valueOf(cursor))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private void persistLog(String itemName, ActivityAction action, CookingLog cookingLog) {
		em.persist(ActivityLog.builder()
			.fridge(fridge)
			.user(member)
			.itemName(itemName)
			.action(action)
			.cookingLog(cookingLog)
			.build());
	}

	private ResultActions addAll(String items) throws Exception {
		return mockMvc.perform(post("/api/fridges/{fridgeId}/items/bulk", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\": [" + items + "]}"));
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
