package com.bium.domain.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.recipe.entity.CookingLog;
import com.bium.domain.recipe.entity.CookingLogItem;
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CookingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private User member;
	private String memberToken;
	private Recipe recipe;

	@BeforeEach
	void setUp() {
		member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());

		recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
		em.persist(recipe);
		ingredient("두부", "1모", IngredientSource.FRIDGE);
		ingredient("대파", "1/2대", IngredientSource.FRIDGE);
		ingredient("간장", "2큰술", IngredientSource.BASIC);
	}

	@Test
	void 레시피_재료마다_뺄_후보를_유통기한이_빠른_순으로_돌려주고_기본_양념은_뺀다() throws Exception {
		FridgeItem lateTofu = item("두부", LocalDate.of(2026, 11, 5));
		FridgeItem earlyTofu = item("순두부", LocalDate.of(2026, 10, 28));
		FridgeItem noExpiryTofu = item("두부", null);
		item("양파", LocalDate.of(2026, 10, 27));

		getCandidates(recipe.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipeId").value(recipe.getId()))
			.andExpect(jsonPath("$.data.ingredients.length()").value(2))
			.andExpect(jsonPath("$.data.ingredients[0].name").value("두부"))
			.andExpect(jsonPath("$.data.ingredients[0].amount").value("1모"))
			.andExpect(jsonPath("$.data.ingredients[0].candidates.length()").value(3))
			.andExpect(jsonPath("$.data.ingredients[0].candidates[0].id").value(earlyTofu.getId()))
			.andExpect(jsonPath("$.data.ingredients[0].candidates[0].version").value(0))
			.andExpect(jsonPath("$.data.ingredients[0].candidates[1].id").value(lateTofu.getId()))
			.andExpect(jsonPath("$.data.ingredients[0].candidates[2].id").value(noExpiryTofu.getId()))
			.andExpect(jsonPath("$.data.ingredients[1].name").value("대파"))
			.andExpect(jsonPath("$.data.ingredients[1].candidates.length()").value(0));
	}

	@Test
	void 확인한_재료를_모두_냉장고에서_빼고_기록을_남긴다() throws Exception {
		FridgeItem tofu = item("두부", LocalDate.of(2026, 10, 28));
		FridgeItem greenOnion = item("대파", LocalDate.of(2026, 10, 30));

		cook(recipe.getId(), memberToken, used(tofu, 0) + "," + used(greenOnion, 0))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.recipeId").value(recipe.getId()))
			.andExpect(jsonPath("$.data.deductedItemIds[0]").value(tofu.getId()))
			.andExpect(jsonPath("$.data.deductedItemIds[1]").value(greenOnion.getId()))
			.andExpect(jsonPath("$.data.cookedAt").isNotEmpty());

		assertThat(reload(tofu).isDeleted()).isTrue();
		assertThat(reload(tofu).getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(reload(greenOnion).isDeleted()).isTrue();

		List<CookingLogItem> logItems = em.createQuery("select i from CookingLogItem i order by i.id",
			CookingLogItem.class).getResultList();
		assertThat(logItems).hasSize(2);
		assertThat(logItems.get(0).getQuantityBefore()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(logItems.get(0).getDeductedQuantity()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(logItems.get(0).isDeletedByDeduction()).isTrue();
	}

	@Test
	void 같은_재료를_두_번_보내도_한_번만_뺀다() throws Exception {
		FridgeItem tofu = item("두부", null);

		cook(recipe.getId(), memberToken, used(tofu, 0) + "," + used(tofu, 0))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.deductedItemIds.length()").value(1));
	}

	@Test
	void 그사이_다른_멤버가_고친_재료가_있으면_아무_재료도_빼지_않는다() throws Exception {
		FridgeItem tofu = item("두부", null);
		FridgeItem greenOnion = item("대파", null);

		cook(recipe.getId(), memberToken, used(tofu, 0) + "," + used(greenOnion, 5))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

		assertThat(reload(tofu).isDeleted()).isFalse();
		assertThat(countCookingLogs()).isZero();
	}

	@Test
	void 다른_냉장고의_재료나_이미_뺀_재료는_뺄_수_없다() throws Exception {
		Fridge otherFridge = em.merge(new Fridge("남의 냉장고"));
		FridgeItem othersTofu = item(otherFridge, "두부", null);
		FridgeItem deletedTofu = item("두부", null);
		deletedTofu.delete(LocalDateTime.of(2026, 10, 20, 12, 0));
		em.flush();

		cook(recipe.getId(), memberToken, used(othersTofu, 0))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));
		cook(recipe.getId(), memberToken, used(deletedTofu, 1))
			.andExpect(status().isNotFound());
		assertThat(reload(othersTofu).isDeleted()).isFalse();
	}

	@Test
	void 뺄_재료를_보내지_않으면_400을_반환한다() throws Exception {
		cook(recipe.getId(), memberToken, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("요리에 쓴 재료를 선택해 주세요."));
	}

	@Test
	void 멤버가_아니면_후보를_보거나_요리를_완료할_수_없다() throws Exception {
		FridgeItem tofu = item("두부", null);
		User stranger = em.merge(new User("stranger", "password", "남"));
		String strangerToken = jwtProvider.createAccessToken(stranger.getId());

		getCandidates(recipe.getId(), strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
		cook(recipe.getId(), strangerToken, used(tofu, 0))
			.andExpect(status().isNotFound());
		assertThat(reload(tofu).isDeleted()).isFalse();
	}

	@Test
	void 되돌리면_뺀_재료를_다시_살리고_다시_되돌릴_수_없다() throws Exception {
		FridgeItem tofu = item("두부", LocalDate.of(2026, 10, 28));
		Long cookingLogId = cookAndGetLogId(tofu);

		undo(cookingLogId, memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(cookingLogId))
			.andExpect(jsonPath("$.data.restoredItemIds[0]").value(tofu.getId()))
			.andExpect(jsonPath("$.data.undoneAt").isNotEmpty());

		FridgeItem restored = reload(tofu);
		assertThat(restored.isDeleted()).isFalse();
		assertThat(restored.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(em.find(CookingLog.class, cookingLogId).isUndone()).isTrue();

		undo(cookingLogId, memberToken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ALREADY_UNDONE"));
		assertThat(reload(tofu).getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
	}

	@Test
	void 멤버가_아니면_되돌릴_수_없다() throws Exception {
		FridgeItem tofu = item("두부", null);
		Long cookingLogId = cookAndGetLogId(tofu);
		User stranger = em.merge(new User("stranger", "password", "남"));

		undo(cookingLogId, jwtProvider.createAccessToken(stranger.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COOKING_LOG_NOT_FOUND"));
		assertThat(reload(tofu).isDeleted()).isTrue();
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/recipes/{recipeId}/cooking-candidates", recipe.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/cooking-logs/{cookingLogId}/undo", 1L))
			.andExpect(status().isUnauthorized());
	}

	private Long cookAndGetLogId(FridgeItem item) throws Exception {
		cook(recipe.getId(), memberToken, used(item, 0)).andExpect(status().isCreated());
		return em.createQuery("select l.id from CookingLog l", Long.class).getSingleResult();
	}

	private long countCookingLogs() {
		return em.createQuery("select count(l) from CookingLog l", Long.class).getSingleResult();
	}

	private void ingredient(String name, String amount, IngredientSource source) {
		em.persist(RecipeIngredient.builder().recipe(recipe).name(name).amount(amount).source(source).build());
	}

	private FridgeItem item(String name, LocalDate expiresAt) {
		return item(fridge, name, expiresAt);
	}

	private FridgeItem item(Fridge target, String name, LocalDate expiresAt) {
		FridgeItem item = FridgeItem.builder()
			.fridge(target)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.expiresAt(expiresAt)
			.build();
		em.persist(item);
		return item;
	}

	private FridgeItem reload(FridgeItem item) {
		em.flush();
		em.clear();
		return em.find(FridgeItem.class, item.getId());
	}

	private String used(FridgeItem item, int version) {
		return "{\"fridgeItemId\": " + item.getId() + ", \"version\": " + version + "}";
	}

	private ResultActions getCandidates(Long recipeId, String token) throws Exception {
		return mockMvc.perform(get("/api/recipes/{recipeId}/cooking-candidates", recipeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions cook(Long recipeId, String token, String items) throws Exception {
		return mockMvc.perform(post("/api/recipes/{recipeId}/cooking-logs", recipeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\": [" + items + "]}"));
	}

	private ResultActions undo(Long cookingLogId, String token) throws Exception {
		return mockMvc.perform(post("/api/cooking-logs/{cookingLogId}/undo", cookingLogId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
