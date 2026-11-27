package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 내가 추가한 도감 재료는 이름·아이콘·카테고리·기본 유통기한을 고칠 수 있고, 기본 재료와 남의 재료는 고칠 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngredientCatalogUpdateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User user;
	private String token;
	private IngredientCatalog kimchi;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("catalog-editor", "password", "사용자"));
		token = jwtProvider.createAccessToken(user.getId());
		kimchi = catalog(user, "엄마표 김치");
	}

	@Test
	void 내_재료의_이름_아이콘_카테고리_기본_유통기한을_고치고_냉장고_재료와의_연결은_그대로_둔다() throws Exception {
		Fridge fridge = em.merge(new Fridge("우리 냉장고"));
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.catalog(kimchi)
			.name("엄마표 김치")
			.emoji("🥬")
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.build();
		em.persist(item);

		update(kimchi.getId(), token, """
			{"name": " 묵은지 ", "emoji": "🥢", "category": "VEGETABLE", "shelfLifeDays": 90}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(kimchi.getId()))
			.andExpect(jsonPath("$.data.name").value("묵은지"))
			.andExpect(jsonPath("$.data.emoji").value("🥢"))
			.andExpect(jsonPath("$.data.category").value("VEGETABLE"))
			.andExpect(jsonPath("$.data.shelfLifeDays").value(90))
			.andExpect(jsonPath("$.data.basic").value(false));

		em.flush();
		em.clear();
		FridgeItem reloaded = em.find(FridgeItem.class, item.getId());
		assertThat(reloaded.getCatalog().getId()).isEqualTo(kimchi.getId());
		assertThat(reloaded.getName()).isEqualTo("엄마표 김치");
	}

	@Test
	void 이름을_그대로_두고_다른_값만_고칠_수_있다() throws Exception {
		update(kimchi.getId(), token, """
			{"name": "엄마표 김치", "emoji": "🥬", "category": "ETC", "shelfLifeDays": 14}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shelfLifeDays").value(14));
	}

	@Test
	void 다른_내_재료나_기본_재료와_이름이_같으면_409를_준다() throws Exception {
		catalog(user, "깍두기");
		catalog(null, "양파");

		update(kimchi.getId(), token, """
			{"name": "깍두기", "emoji": "🥬", "category": "ETC", "shelfLifeDays": 30}
			""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_CATALOG_NAME"));
		update(kimchi.getId(), token, """
			{"name": "양파", "emoji": "🧅", "category": "VEGETABLE", "shelfLifeDays": 30}
			""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_CATALOG_NAME"));
	}

	@Test
	void 기본_재료와_남의_재료는_고칠_수_없고_없는_재료와_같은_404를_준다() throws Exception {
		IngredientCatalog onion = catalog(null, "양파");
		User other = em.merge(new User("catalog-other", "password", "남"));
		IngredientCatalog othersCatalog = catalog(other, "남의 재료");
		String body = """
			{"name": "새 이름", "emoji": "🥬", "category": "ETC", "shelfLifeDays": 30}
			""";

		update(onion.getId(), token, body)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
		update(othersCatalog.getId(), token, body)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));

		em.flush();
		em.clear();
		assertThat(em.find(IngredientCatalog.class, onion.getId()).getName()).isEqualTo("양파");
	}

	@Test
	void 추가할_때와_같은_규칙으로_입력을_검사한다() throws Exception {
		update(kimchi.getId(), token, """
			{"name": "묵은지", "emoji": "김치", "category": "ETC", "shelfLifeDays": 30}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		update(kimchi.getId(), token, """
			{"name": "묵은지", "emoji": "🥬", "category": "ETC", "shelfLifeDays": 366}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("기본 유통기한은 1~365일 사이로 입력해 주세요."));
	}

	private IngredientCatalog catalog(User owner, String name) {
		IngredientCatalog catalog = IngredientCatalog.builder()
			.owner(owner)
			.name(name)
			.emoji("🥬")
			.category(IngredientCategory.ETC)
			.shelfLifeDays(7)
			.build();
		em.persist(catalog);
		return catalog;
	}

	private ResultActions update(Long catalogId, String accessToken, String body) throws Exception {
		return mockMvc.perform(put("/api/ingredient-catalogs/{catalogId}", catalogId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
