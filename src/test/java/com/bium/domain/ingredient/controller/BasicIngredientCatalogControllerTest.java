package com.bium.domain.ingredient.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 공용 도감의 기본 재료(owner_id NULL)를 내 재료와 함께 보여 주고, 냉장고에 넣기·장본 음식 담기·즐겨찾기에 쓸 수 있는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BasicIngredientCatalogControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private String token;
	private User user;
	private Fridge fridge;
	private IngredientCatalog onion;
	private IngredientCatalog tofu;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("user", "password", "사용자"));
		token = jwtProvider.createAccessToken(user.getId());
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, user, MemberRole.OWNER));
		onion = catalog(null, "양파", IngredientCategory.VEGETABLE);
		tofu = catalog(null, "두부", IngredientCategory.ETC);
	}

	@Test
	void 목록에_내_재료_다음에_기본_재료를_basic이_true로_준다() throws Exception {
		catalog(user, "엄마표 김치", IngredientCategory.ETC);

		mockMvc.perform(get("/api/ingredient-catalogs").header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.ingredients.length()").value(3))
			.andExpect(jsonPath("$.data.ingredients[0].name").value("엄마표 김치"))
			.andExpect(jsonPath("$.data.ingredients[0].basic").value(false))
			.andExpect(jsonPath("$.data.ingredients[1].name").value("양파"))
			.andExpect(jsonPath("$.data.ingredients[1].basic").value(true))
			.andExpect(jsonPath("$.data.ingredients[2].name").value("두부"));
	}

	@Test
	void 내_재료와_이름이_같은_기본_재료는_목록에서_뺀다() throws Exception {
		IngredientCatalog myOnion = catalog(user, "양파", IngredientCategory.VEGETABLE);

		mockMvc.perform(get("/api/ingredient-catalogs").header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.ingredients.length()").value(2))
			.andExpect(jsonPath("$.data.ingredients[0].id").value(myOnion.getId()))
			.andExpect(jsonPath("$.data.ingredients[0].basic").value(false))
			.andExpect(jsonPath("$.data.ingredients[1].name").value("두부"));
	}

	@Test
	void 기본_재료와_같은_이름은_도감에_추가할_수_없다() throws Exception {
		mockMvc.perform(post("/api/ingredient-catalogs").header(auth(), bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": " 양파 ", "emoji": "🧅", "category": "VEGETABLE", "shelfLifeDays": 30}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_CATALOG_NAME"));
	}

	@Test
	void 기본_재료는_즐겨찾기하면_목록_맨_위에_온다() throws Exception {
		mockMvc.perform(post("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()).header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.favorite").value(true))
			.andExpect(jsonPath("$.data.basic").value(true));

		mockMvc.perform(get("/api/ingredient-catalogs").header(auth(), bearer()))
			.andExpect(jsonPath("$.data.ingredients[0].name").value("두부"))
			.andExpect(jsonPath("$.data.ingredients[0].favorite").value(true));
	}

	@Test
	void 기본_재료는_지울_수_없고_없는_재료와_같은_404를_준다() throws Exception {
		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}", onion.getId()).header(auth(), bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
	}

	@Test
	void 기본_재료로_냉장고에_넣는다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/items", fridge.getId()).header(auth(), bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"catalogId": %d, "name": "양파", "emoji": "🧅", "storage": "ROOM"}
					""".formatted(onion.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("양파"));
	}

	@Test
	void 기본_재료를_장본_음식에_담는다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/purchased-items", fridge.getId()).header(auth(), bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"catalogId": %d}
					""".formatted(tofu.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("두부"));
	}

	@Test
	void 다른_사용자의_재료로는_냉장고에_넣을_수_없다() throws Exception {
		User other = em.merge(new User("other", "password", "다른 사용자"));
		IngredientCatalog othersCatalog = catalog(other, "다른 사람 재료", IngredientCategory.ETC);

		mockMvc.perform(post("/api/fridges/{fridgeId}/items", fridge.getId()).header(auth(), bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"catalogId": %d, "name": "다른 사람 재료", "emoji": "🥬", "storage": "FRIDGE"}
					""".formatted(othersCatalog.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
	}

	private IngredientCatalog catalog(User owner, String name, IngredientCategory category) {
		IngredientCatalog catalog = IngredientCatalog.builder()
			.owner(owner)
			.name(name)
			.emoji("🥬")
			.category(category)
			.defaultStorage(StorageType.FRIDGE)
			.shelfLifeDays(7)
			.build();
		em.persist(catalog);
		return catalog;
	}

	private String auth() {
		return HttpHeaders.AUTHORIZATION;
	}

	private String bearer() {
		return "Bearer " + token;
	}

}
