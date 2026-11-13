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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngredientCatalogFavoriteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private String token;
	private IngredientCatalog tofu;
	private IngredientCatalog milk;

	@BeforeEach
	void setUp() {
		User user = em.merge(new User("user", "password", "사용자"));
		token = jwtProvider.createAccessToken(user.getId());
		tofu = catalog(user, "두부");
		milk = catalog(user, "우유");
	}

	@Test
	void 즐겨찾기하면_favorite이_true인_재료를_주고_목록_맨_위에_온다() throws Exception {
		mockMvc.perform(post("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()).header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(tofu.getId()))
			.andExpect(jsonPath("$.data.favorite").value(true));

		mockMvc.perform(get("/api/ingredient-catalogs").header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.ingredients[0].name").value("두부"))
			.andExpect(jsonPath("$.data.ingredients[0].favorite").value(true))
			.andExpect(jsonPath("$.data.ingredients[1].name").value("우유"))
			.andExpect(jsonPath("$.data.ingredients[1].favorite").value(false));
	}

	@Test
	void 즐겨찾기를_해제하면_favorite이_false인_재료를_준다() throws Exception {
		mockMvc.perform(post("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()).header(auth(), bearer()));

		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()).header(auth(), bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.favorite").value(false));

		mockMvc.perform(get("/api/ingredient-catalogs").header(auth(), bearer()))
			.andExpect(jsonPath("$.data.ingredients[0].name").value("우유"));
	}

	@Test
	void 다른_사용자의_재료면_404를_반환한다() throws Exception {
		User other = em.merge(new User("other", "password", "남"));
		IngredientCatalog othersCatalog = catalog(other, "남의 두부");

		mockMvc.perform(post("/api/ingredient-catalogs/{catalogId}/favorite", othersCatalog.getId())
				.header(auth(), bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}/favorite", othersCatalog.getId())
				.header(auth(), bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}/favorite", tofu.getId()))
			.andExpect(status().isUnauthorized());
	}

	private IngredientCatalog catalog(User owner, String name) {
		IngredientCatalog catalog = IngredientCatalog.builder().owner(owner).name(name).emoji("🥬").build();
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
