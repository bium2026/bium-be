package com.bium.domain.nutrition.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.service.NutritionPrefetcher;
import com.bium.domain.user.entity.User;
import com.bium.global.nutrition.FoodNutritionClient;
import com.bium.global.nutrition.FoodNutritionClient.Food;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NutritionSummaryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	@MockitoBean
	private FoodNutritionClient foodNutritionClient;

	@MockitoBean
	private NutritionPrefetcher nutritionPrefetcher;

	private User member;
	private Fridge fridge;
	private String memberToken;

	@BeforeEach
	void setUp() {
		member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
	}

	@Test
	void 저장된_영양_정보를_100g씩_더하고_못_찾아본_재료는_백그라운드에서_찾는다() throws Exception {
		saveNutrition("두부", "100", "84", "1.8", "9.3", "4.6");
		// 기준량이 200g이면 100g당 값으로 바꿔 더한다
		saveNutrition("우유", "200", "130", "9.6", "6.4", "7.2");
		saveNutrition("계란", "100", "136", "1.4", "12.4", null);
		saveNotFound("수제 소스", LocalDateTime.now(clock));
		FridgeItem tofu = saveItem("두부", StorageType.FRIDGE, null);
		FridgeItem milk = saveItem("우유", StorageType.FRIDGE, null);
		saveItem("계란", StorageType.FRIDGE, null);
		saveItem("수제 소스", StorageType.FRIDGE, null);
		saveItem("김치", StorageType.FRIDGE, null);
		FridgeItem deleted = saveItem("두부", StorageType.FRIDGE, null);
		deleted.delete(LocalDateTime.now(clock));
		em.flush();

		getSummary(fridge.getId(), null, memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.itemCount").value(5))
			.andExpect(jsonPath("$.data.matchedCount").value(2))
			.andExpect(jsonPath("$.data.pendingCount").value(1))
			.andExpect(jsonPath("$.data.caloriesKcal").value(149.0))
			.andExpect(jsonPath("$.data.carbohydrateG").value(6.6))
			.andExpect(jsonPath("$.data.proteinG").value(12.5))
			.andExpect(jsonPath("$.data.fatG").value(8.2))
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].itemId").value(tofu.getId()))
			.andExpect(jsonPath("$.data.items[0].name").value("두부"))
			.andExpect(jsonPath("$.data.items[0].foodName").value("두부"))
			.andExpect(jsonPath("$.data.items[1].itemId").value(milk.getId()))
			.andExpect(jsonPath("$.data.items[1].baseAmountG").value(200.0))
			.andExpect(jsonPath("$.data.items[1].caloriesKcal").value(130.0));

		verify(nutritionPrefetcher).prefetch(argThat((Collection<String> names) -> names.size() == 1
			&& names.contains("김치")));
		verifyNoInteractions(foodNutritionClient);
	}

	@Test
	void 같은_재료가_두_줄이면_두_번_더한다() throws Exception {
		saveNutrition("두부", "100", "84", "1.8", "9.3", "4.6");
		saveItem("두부", StorageType.FRIDGE, null);
		saveItem("두부", StorageType.FREEZER, null);

		getSummary(fridge.getId(), null, memberToken)
			.andExpect(jsonPath("$.data.matchedCount").value(2))
			.andExpect(jsonPath("$.data.caloriesKcal").value(168.0));
	}

	@Test
	void 도감_재료는_도감_이름으로_찾은_영양_정보를_더한다() throws Exception {
		saveNutrition("두부", "100", "84", "1.8", "9.3", "4.6");
		IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
			.owner(member)
			.name("두부")
			.defaultStorage(StorageType.FRIDGE)
			.build());
		saveItem("엄마가 준 두부", StorageType.FRIDGE, catalog);

		getSummary(fridge.getId(), null, memberToken)
			.andExpect(jsonPath("$.data.matchedCount").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("엄마가 준 두부"))
			.andExpect(jsonPath("$.data.items[0].foodName").value("두부"));
	}

	@Test
	void 구역을_고르면_그_구역_재료만_더하고_그_구역에서_못_찾아본_재료만_찾는다() throws Exception {
		saveNutrition("두부", "100", "84", "1.8", "9.3", "4.6");
		saveItem("두부", StorageType.FRIDGE, null);
		saveItem("만두", StorageType.FREEZER, null);

		getSummary(fridge.getId(), "FRIDGE", memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemCount").value(1))
			.andExpect(jsonPath("$.data.matchedCount").value(1))
			.andExpect(jsonPath("$.data.pendingCount").value(0));
		verify(nutritionPrefetcher, never()).prefetch(any());

		getSummary(fridge.getId(), "FREEZER", memberToken)
			.andExpect(jsonPath("$.data.itemCount").value(1))
			.andExpect(jsonPath("$.data.matchedCount").value(0))
			.andExpect(jsonPath("$.data.pendingCount").value(1));
		verify(nutritionPrefetcher).prefetch(argThat((Collection<String> names) -> names.contains("만두")));
	}

	@Test
	void 합계에_들어간_재료가_없으면_합계를_null로_준다() throws Exception {
		saveItem("만두", StorageType.FREEZER, null);

		getSummary(fridge.getId(), null, memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemCount").value(1))
			.andExpect(jsonPath("$.data.matchedCount").value(0))
			.andExpect(jsonPath("$.data.caloriesKcal").isEmpty())
			.andExpect(jsonPath("$.data.carbohydrateG").isEmpty())
			.andExpect(jsonPath("$.data.proteinG").isEmpty())
			.andExpect(jsonPath("$.data.fatG").isEmpty())
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 맞는_식품이_없던_지_30일이_지난_이름은_다시_찾으려고_pending으로_센다() throws Exception {
		saveNotFound("김치", LocalDateTime.now(clock).minusDays(31));
		saveItem("김치", StorageType.FRIDGE, null);

		getSummary(fridge.getId(), null, memberToken)
			.andExpect(jsonPath("$.data.pendingCount").value(1));

		verify(nutritionPrefetcher).prefetch(argThat((Collection<String> names) -> names.contains("김치")));
	}

	@Test
	void 잘못된_구역은_400을_반환한다() throws Exception {
		getSummary(fridge.getId(), "KITCHEN", memberToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		getSummary(fridge.getId(), "fridge", memberToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 멤버가_아니면_404를_반환한다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));

		getSummary(fridge.getId(), null, jwtProvider.createAccessToken(stranger.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/nutrition-summary", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getSummary(Long fridgeId, String storage, String token) throws Exception {
		var request = get("/api/fridges/{fridgeId}/nutrition-summary", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (storage != null) {
			request.param("storage", storage);
		}
		return mockMvc.perform(request);
	}

	private FridgeItem saveItem(String name, StorageType storage, IngredientCatalog catalog) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.catalog(catalog)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(storage)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.build();
		em.persist(item);
		return item;
	}

	private void saveNutrition(String name, String baseAmount, String calories, String carbohydrate, String protein,
		String fat) {
		FoodNutrition nutrition = new FoodNutrition(name);
		nutrition.update(new Food("F-" + name, name, "원재료성", new BigDecimal(baseAmount), decimal(calories),
			decimal(carbohydrate), decimal(protein), decimal(fat)), LocalDateTime.now(clock));
		em.persist(nutrition);
	}

	private void saveNotFound(String name, LocalDateTime fetchedAt) {
		FoodNutrition nutrition = new FoodNutrition(name);
		nutrition.update(null, fetchedAt);
		em.persist(nutrition);
	}

	private static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}

}
