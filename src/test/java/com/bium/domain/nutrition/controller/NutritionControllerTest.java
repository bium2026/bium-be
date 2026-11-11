package com.bium.domain.nutrition.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

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
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.nutrition.FoodNutritionClient;
import com.bium.global.nutrition.FoodNutritionClient.Food;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NutritionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private FoodNutritionRepository foodNutritionRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private FoodNutritionClient foodNutritionClient;

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
	void 도감에_없는_재료는_재료_이름으로_찾아_100g당_영양_정보를_준다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(
			food("D1", "두부조림", "120", "8", "7", "6"),
			food("R2", "두부", "84", "1.8", "9.3", "4.6")));

		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.foodName").value("두부"))
			.andExpect(jsonPath("$.data.baseAmountG").value(100.0))
			.andExpect(jsonPath("$.data.caloriesKcal").value(84.0))
			.andExpect(jsonPath("$.data.carbohydrateG").value(1.8))
			.andExpect(jsonPath("$.data.proteinG").value(9.3))
			.andExpect(jsonPath("$.data.fatG").value(4.6));
	}

	@Test
	void 도감_재료는_냉장고에서_고친_이름이_아니라_도감_이름으로_찾는다() throws Exception {
		IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
			.owner(member)
			.name("두부")
			.defaultStorage(StorageType.FRIDGE)
			.build());
		FridgeItem tofu = saveItem("엄마가 준 두부", catalog);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(food("R1", "두부", "84", "1.8", "9.3", "4.6")));

		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.foodName").value("두부"));

		verify(foodNutritionClient).searchByName("두부");
	}

	@Test
	void 한_번_찾은_이름은_다른_재료라도_다시_부르지_않는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		FridgeItem otherTofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(food("R1", "두부", "84", "1.8", "9.3", "4.6")));

		getNutrition(tofu.getId(), memberToken).andExpect(jsonPath("$.data.foodName").value("두부"));
		getNutrition(otherTofu.getId(), memberToken).andExpect(jsonPath("$.data.foodName").value("두부"));

		verify(foodNutritionClient, times(1)).searchByName("두부");
	}

	@Test
	void 맞는_식품이_없으면_data를_null로_주고_결과를_저장해_다시_부르지_않는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(food("D1", "두부조림", "120", "8", "7", "6")));

		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").isEmpty());
		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isEmpty());

		verify(foodNutritionClient, times(1)).searchByName("두부");
		assertThat(foodNutritionRepository.findBySearchName("두부"))
			.hasValueSatisfying(saved -> assertThat(saved.isFound()).isFalse());
	}

	@Test
	void 맞는_식품이_없던_이름은_30일이_지나면_다시_찾는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of());
		getNutrition(tofu.getId(), memberToken).andExpect(jsonPath("$.data").isEmpty());

		em.createQuery("update FoodNutrition set fetchedAt = :fetchedAt where searchName = '두부'")
			.setParameter("fetchedAt", LocalDateTime.now(clock).minusDays(31))
			.executeUpdate();
		em.clear();
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(food("R1", "두부", "84", "1.8", "9.3", "4.6")));

		getNutrition(tofu.getId(), memberToken).andExpect(jsonPath("$.data.foodName").value("두부"));

		verify(foodNutritionClient, times(2)).searchByName("두부");
	}

	@Test
	void 맞는_식품을_찾은_이름은_30일이_지나도_다시_부르지_않는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName("두부")).willReturn(List.of(food("R1", "두부", "84", "1.8", "9.3", "4.6")));
		getNutrition(tofu.getId(), memberToken).andExpect(jsonPath("$.data.foodName").value("두부"));

		em.createQuery("update FoodNutrition set fetchedAt = :fetchedAt where searchName = '두부'")
			.setParameter("fetchedAt", LocalDateTime.now(clock).minusDays(365))
			.executeUpdate();
		em.clear();

		getNutrition(tofu.getId(), memberToken).andExpect(jsonPath("$.data.foodName").value("두부"));

		verify(foodNutritionClient, times(1)).searchByName("두부");
	}

	@Test
	void 식약처_호출이_실패하면_502를_주고_저장하지_않는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		given(foodNutritionClient.searchByName(anyString()))
			.willThrow(new BusinessException(ErrorCode.NUTRITION_FETCH_FAILED));

		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("NUTRITION_FETCH_FAILED"))
			.andExpect(jsonPath("$.error.message").value("영양 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."));

		assertThat(foodNutritionRepository.findBySearchName("두부")).isEmpty();
	}

	@Test
	void 다른_냉장고의_재료는_404를_반환하고_식약처를_부르지_않는다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		User stranger = em.merge(new User("stranger", "password", "남"));
		String strangerToken = jwtProvider.createAccessToken(stranger.getId());

		getNutrition(tofu.getId(), strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));

		verifyNoInteractions(foodNutritionClient);
	}

	@Test
	void 지운_재료는_404를_반환한다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);
		tofu.delete(LocalDateTime.now(clock));
		em.flush();

		getNutrition(tofu.getId(), memberToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		FridgeItem tofu = saveItem("두부", null);

		mockMvc.perform(get("/api/fridge-items/{itemId}/nutrition", tofu.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getNutrition(Long itemId, String token) throws Exception {
		return mockMvc.perform(get("/api/fridge-items/{itemId}/nutrition", itemId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private FridgeItem saveItem(String name, IngredientCatalog catalog) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.catalog(catalog)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.build();
		em.persist(item);
		return item;
	}

	private static Food food(String foodCode, String foodName, String calories, String carbohydrate, String protein,
		String fat) {
		return new Food(foodCode, foodName, "원재료성", new BigDecimal("100.00"), new BigDecimal(calories),
			new BigDecimal(carbohydrate), new BigDecimal(protein), new BigDecimal(fat));
	}

}
