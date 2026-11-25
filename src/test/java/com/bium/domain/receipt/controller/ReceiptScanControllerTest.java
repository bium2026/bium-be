package com.bium.domain.receipt.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.recipe.recommendation.RecommendationRateLimiter;
import com.bium.domain.user.entity.User;
import com.bium.global.gemini.GeminiClient;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceiptScanControllerTest {

	private static final MockMultipartFile IMAGE = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private GeminiClient geminiClient;

	@MockitoBean
	private RecommendationRateLimiter rateLimiter;

	private Fridge fridge;
	private String token;

	@BeforeEach
	void setUp() {
		User me = em.merge(new User("me", "password", "강희"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, me, MemberRole.OWNER));
		token = jwtProvider.createAccessToken(me.getId());
	}

	@Test
	void 영수증_사진이면_읽은_재료_후보를_돌려준다() throws Exception {
		givenGeminiAnswers("""
			{"isReceipt": true, "items": [{"receiptText": "서울우유 1L", "name": "우유", "emoji": "🥛", "storage": "FRIDGE", "shelfLifeDays": 10}]}
			""");

		scan()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("우유"));
	}

	@Test
	void 기본_재료와_이름이_같으면_도감_아이콘을_쓰고_다른_재료는_모델_아이콘을_쓴다() throws Exception {
		em.persist(IngredientCatalog.builder().name("두부").emoji("🧈").category(IngredientCategory.ETC)
			.defaultStorage(StorageType.FRIDGE).shelfLifeDays(5).build());
		em.persist(IngredientCatalog.builder().name("우유").emoji("🥛").category(IngredientCategory.ETC)
			.defaultStorage(StorageType.FRIDGE).shelfLifeDays(10).build());
		givenGeminiAnswers("""
			{"isReceipt": true, "items": [
			  {"receiptText": "풀무원 두부", "name": "두부", "emoji": "⬜", "storage": "FRIDGE", "shelfLifeDays": 5},
			  {"receiptText": "서울우유 1L", "name": "우유", "emoji": "🍼", "storage": "FRIDGE", "shelfLifeDays": 10},
			  {"receiptText": "곤약 300g", "name": "곤약", "emoji": "⬜", "storage": "FRIDGE", "shelfLifeDays": 30},
			  {"receiptText": "비비고 왕교자", "name": "왕교자 만두", "emoji": "🥟", "storage": "FREEZER", "shelfLifeDays": 90}
			]}
			""");

		scan()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].emoji").value("🧈"))
			.andExpect(jsonPath("$.data.items[1].emoji").value("🥛"))
			.andExpect(jsonPath("$.data.items[2].emoji").value(nullValue()))
			.andExpect(jsonPath("$.data.items[3].emoji").value("🥟"));
	}

	@Test
	void 영수증이_아닌_사진이면_재료를_돌려주지_않고_인식_실패로_알린다() throws Exception {
		givenGeminiAnswers("""
			{"isReceipt": false, "items": [{"receiptText": "사과", "name": "사과", "emoji": "🍎", "storage": "FRIDGE", "shelfLifeDays": 14}]}
			""");

		scan()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("RECEIPT_NOT_RECOGNIZED"))
			.andExpect(jsonPath("$.error.message").value("영수증 인식에 실패했습니다. 영수증이 잘 보이게 다시 찍어 주세요."));
	}

	private void givenGeminiAnswers(String json) {
		given(geminiClient.generateJson(anyString(), any(byte[].class), anyString(), anyDouble())).willReturn(json);
	}

	private ResultActions scan() throws Exception {
		return mockMvc.perform(multipart("/api/fridges/{fridgeId}/receipt-scans", fridge.getId())
			.file(IMAGE)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
