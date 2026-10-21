package com.bium.domain.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.bium.domain.recipe.recommendation.RecommendationRateLimiter;
import com.bium.domain.recipe.repository.RecipeRepository;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.exception.TooManyRequestsException;
import com.bium.global.gemini.GeminiClient;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipeControllerTest {

	private static final String GEMINI_RESPONSE = """
		{"recipes": [
		  {"name": "두부 계란 볶음밥", "cookTimeMinutes": 12, "difficulty": "EASY", "servings": 1,
		   "caloriesKcal": 430, "emoji": "🍳", "summary": "두부와 계란으로 만드는 볶음밥",
		   "ingredients": [
		     {"name": "두부", "amount": "1/2모", "required": true},
		     {"name": "계란", "amount": "2개", "required": true},
		     {"name": "간장", "amount": "1큰술", "required": true}],
		   "steps": ["두부를 굽는다", "계란과 밥을 볶는다"], "tips": ["찬밥을 쓴다"],
		   "nutrition": {"proteinG": 24, "carbohydrateG": 52, "fatG": 14},
		   "fridgeMatches": ["두부", "계란"]}
		]}
		""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private GeminiClient geminiClient;

	@MockitoBean
	private RecommendationRateLimiter rateLimiter;

	private User owner;
	private Fridge fridge;
	private String ownerToken;
	private LocalDate today;

	@BeforeEach
	void setUp() {
		today = LocalDate.now(clock);
		owner = em.merge(new User("owner", "password", "주인"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		ownerToken = jwtProvider.createAccessToken(owner.getId());
	}

	@Test
	void 냉장고_재료로_추천받은_레시피를_저장하고_돌려준다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		saveItem(fridge, "계란", today);
		saveItem(fridge, "우유", today.minusDays(1));
		FridgeItem deletedBeef = saveItem(fridge, "소고기", today.plusDays(3));
		em.createQuery("update FridgeItem set deletedAt = :now where id = :id")
			.setParameter("now", LocalDateTime.now(clock))
			.setParameter("id", deletedBeef.getId())
			.executeUpdate();
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);

		recommend(fridge.getId(), ownerToken, "{\"prioritizeUrgent\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.recipes.length()").value(1))
			.andExpect(jsonPath("$.data.recipes[0].id").isNumber())
			.andExpect(jsonPath("$.data.recipes[0].name").value("두부 계란 볶음밥"))
			.andExpect(jsonPath("$.data.recipes[0].difficulty").value("EASY"))
			.andExpect(jsonPath("$.data.recipes[0].ingredients[0].amount").value("1/2모"))
			.andExpect(jsonPath("$.data.recipes[0].ingredients[2].source").value("BASIC"))
			.andExpect(jsonPath("$.data.recipes[0].fridgeMatches[0]").value("두부"))
			.andExpect(jsonPath("$.data.recipes[0].fridgeMatches[1]").value("계란"))
			.andExpect(jsonPath("$.data.recipes[0].nutrition.proteinG").value(24));

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(geminiClient).generateJson(prompt.capture());
		assertThat(prompt.getValue()).contains("두부", "계란").doesNotContain("우유", "소고기");
		verify(rateLimiter).check(String.valueOf(owner.getId()));
		assertThat(recipeRepository.count()).isEqualTo(1);
	}

	@Test
	void 본문_없이도_추천받는다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		saveItem(fridge, "계란", today);
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);

		mockMvc.perform(post("/api/fridges/{fridgeId}/recipes/recommendations", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isOk());
	}

	@Test
	void 저장한_레시피를_상세_조회한다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		saveItem(fridge, "계란", today);
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);
		long recipeId = recommendAndGetFirstRecipeId();
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/recipes/{recipeId}", recipeId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(recipeId))
			.andExpect(jsonPath("$.data.name").value("두부 계란 볶음밥"))
			.andExpect(jsonPath("$.data.emoji").value("🍳"))
			.andExpect(jsonPath("$.data.cookTimeMinutes").value(12))
			.andExpect(jsonPath("$.data.steps[1]").value("계란과 밥을 볶는다"))
			.andExpect(jsonPath("$.data.tips[0]").value("찬밥을 쓴다"))
			.andExpect(jsonPath("$.data.ingredients.length()").value(3))
			.andExpect(jsonPath("$.data.ingredients[1].required").value(true))
			.andExpect(jsonPath("$.data.fridgeMatches.length()").value(2))
			.andExpect(jsonPath("$.data.nutrition.fatG").value(14))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());
	}

	@Test
	void 냉장고_멤버가_아니면_추천받을_수_없다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));

		recommend(fridge.getId(), jwtProvider.createAccessToken(stranger.getId()), "{}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		verify(geminiClient, never()).generateJson(anyString());
		verify(rateLimiter, never()).check(anyString());
	}

	@Test
	void 다른_냉장고의_레시피는_조회할_수_없다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		saveItem(fridge, "계란", today);
		given(geminiClient.generateJson(anyString())).willReturn(GEMINI_RESPONSE);
		long recipeId = recommendAndGetFirstRecipeId();
		User stranger = em.merge(new User("stranger", "password", "남"));

		mockMvc.perform(get("/api/recipes/{recipeId}", recipeId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(stranger.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/recipes/recommendations", fridge.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/recipes/{recipeId}", 1))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 쓸_수_있는_재료가_없으면_422를_반환한다() throws Exception {
		saveItem(fridge, "우유", today.minusDays(1));

		recommend(fridge.getId(), ownerToken, "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error.code").value("NO_USABLE_INGREDIENT"));
		verify(geminiClient, never()).generateJson(anyString());
		verify(rateLimiter, never()).check(anyString());
	}

	@Test
	void 호출_제한을_넘으면_429와_다시_시도할_시간을_반환한다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		willThrow(new TooManyRequestsException(42)).given(rateLimiter).check(anyString());

		recommend(fridge.getId(), ownerToken, "{}")
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
			.andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
		verify(geminiClient, never()).generateJson(anyString());
	}

	@Test
	void Gemini_사용_한도를_넘으면_503을_반환하고_저장하지_않는다() throws Exception {
		saveItem(fridge, "두부", today.plusDays(2));
		given(geminiClient.generateJson(anyString())).willThrow(new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED));

		recommend(fridge.getId(), ownerToken, "{}")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"));
		assertThat(recipeRepository.count()).isZero();
	}

	@Test
	void JSON_응답을_받을_수_없으면_Gemini를_호출하지_않는다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/recipes/recommendations", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
				.accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isNotAcceptable());
		verify(geminiClient, never()).generateJson(anyString());
	}

	private long recommendAndGetFirstRecipeId() throws Exception {
		String body = recommend(fridge.getId(), ownerToken, "{}")
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		JsonNode response = JsonMapper.shared().readTree(body);
		return response.get("data").get("recipes").get(0).get("id").asLong();
	}

	private ResultActions recommend(Long fridgeId, String token, String body) throws Exception {
		return mockMvc.perform(post("/api/fridges/{fridgeId}/recipes/recommendations", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private FridgeItem saveItem(Fridge fridge, String name, LocalDate expiresAt) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
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

}
