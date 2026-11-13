package com.bium.domain.nutrition.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.nutrition.service.NutritionPrefetcher;
import com.bium.domain.shopping.entity.PurchaseSource;
import com.bium.domain.shopping.entity.PurchasedItem;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 재료를 넣는 세 경로(하나씩 넣기, 일괄 넣기, 장본 음식 옮기기)가 영양 정보를 미리 찾도록 넘기는지 확인한다.
 * 커밋된 뒤에만 찾는 동작은 NutritionPrefetcherTest에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NutritionPrefetchOnAddTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

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
	void 도감_재료를_넣으면_영양_정보를_미리_찾는다() throws Exception {
		IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
			.owner(member)
			.name("두부")
			.defaultStorage(StorageType.FRIDGE)
			.build());

		mockMvc.perform(post("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"catalogId\": " + catalog.getId() + ", \"name\": \"엄마가 준 두부\", \"storage\": \"FRIDGE\"}"))
			.andExpect(status().isCreated());

		verify(nutritionPrefetcher).prefetchAfterCommit(argThat(items -> names(items).equals(List.of("엄마가 준 두부"))));
	}

	@Test
	void 영수증_재료를_한_번에_넣으면_넣은_재료_모두의_영양_정보를_미리_찾는다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/items/bulk", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"items": [{"name": "우유", "storage": "FRIDGE"}, {"name": "냉동 만두", "storage": "FREEZER"}]}
					"""))
			.andExpect(status().isCreated());

		verify(nutritionPrefetcher).prefetchAfterCommit(argThat(items -> names(items).equals(List.of("우유", "냉동 만두"))));
	}

	@Test
	void 장본_음식을_냉장고로_옮기면_영양_정보를_미리_찾는다() throws Exception {
		PurchasedItem tofu = PurchasedItem.builder()
			.fridge(fridge)
			.user(member)
			.name("두부")
			.storage(StorageType.FRIDGE)
			.purchasedAt(LocalDate.of(2026, 11, 10))
			.source(PurchaseSource.MANUAL)
			.build();
		em.persist(tofu);

		mockMvc.perform(post("/api/purchased-items/{purchasedItemId}/move", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"storage\": \"FRIDGE\", \"section\": \"CENTER\", \"index\": 0}"))
			.andExpect(status().isCreated());

		verify(nutritionPrefetcher).prefetchAfterCommit(argThat(items -> names(items).equals(List.of("두부"))));
	}

	private static List<String> names(Collection<FridgeItem> items) {
		return items.stream().map(FridgeItem::getName).toList();
	}

}
