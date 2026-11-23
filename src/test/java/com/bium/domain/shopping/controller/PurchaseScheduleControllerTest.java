package com.bium.domain.shopping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseScheduleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

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
	void 구매일_평균_간격으로_주기를_구하고_마지막_구매일에_더해_다음_구매일을_준다() throws Exception {
		// 11-06, 11-13, 11-20 -> (14일 / 2) = 7일
		saveItem(fridge, "우유", "🥛", null, "2026-11-06", null);
		saveItem(fridge, "우유", "🥛", null, "2026-11-13", null);
		saveItem(fridge, "우유", "🥛", null, "2026-11-20", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("우유"))
			.andExpect(jsonPath("$.data.items[0].emoji").value("🥛"))
			.andExpect(jsonPath("$.data.items[0].lastPurchasedAt").value("2026-11-20"))
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(3))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(7))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-11-27"));
	}

	@Test
	void 주기는_반올림한다() throws Exception {
		// 간격 10일 / 2 = 5, 11일 / 2 = 5.5 -> 6, 10일 / 3 = 3.33 -> 3
		saveItem(fridge, "a", null, null, "2026-11-01", null);
		saveItem(fridge, "a", null, null, "2026-11-06", null);
		saveItem(fridge, "a", null, null, "2026-11-11", null);
		saveItem(fridge, "b", null, null, "2026-11-01", null);
		saveItem(fridge, "b", null, null, "2026-11-06", null);
		saveItem(fridge, "b", null, null, "2026-11-12", null);
		saveItem(fridge, "c", null, null, "2026-11-01", null);
		saveItem(fridge, "c", null, null, "2026-11-04", null);
		saveItem(fridge, "c", null, null, "2026-11-08", null);
		saveItem(fridge, "c", null, null, "2026-11-11", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(3))
			.andExpect(jsonPath("$.data.items[?(@.name=='a')].cycleDays").value(5))
			.andExpect(jsonPath("$.data.items[?(@.name=='b')].cycleDays").value(6))
			.andExpect(jsonPath("$.data.items[?(@.name=='c')].cycleDays").value(3));
	}

	@Test
	void 같은_날_여러_번_넣은_재료는_구매일_하나로_센다() throws Exception {
		saveItem(fridge, "달걀", null, null, "2026-11-10", null);
		saveItem(fridge, "달걀", null, null, "2026-11-10", null);
		saveItem(fridge, "달걀", null, null, "2026-11-14", null);
		saveItem(fridge, "달걀", null, null, "2026-11-14", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(2))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(4))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-11-18"));
	}

	@Test
	void 같은_날_구매만_둘이면_구매일이_하나라서_재료의_구매_주기를_쓴다() throws Exception {
		saveItem(fridge, "달걀", null, 10, "2026-11-10", null);
		saveItem(fridge, "달걀", null, 10, "2026-11-10", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(1))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(10))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-11-20"));
	}

	@Test
	void 지운_재료도_구매_기록으로_센다() throws Exception {
		FridgeItem deleted = saveItem(fridge, "두부", null, null, "2026-11-01", null);
		deleted.delete(LocalDateTime.of(2026, 11, 5, 9, 0));
		saveItem(fridge, "두부", null, null, "2026-11-08", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(2))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(7))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-11-15"));
	}

	@Test
	void 도감_재료는_도감_이름으로_묶고_도감이_없으면_앞뒤_공백을_뗀_이름으로_묶는다() throws Exception {
		IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
			.owner(member)
			.name("우유")
			.defaultStorage(StorageType.FRIDGE)
			.build());
		saveItem(fridge, "서울우유", "🥛", null, "2026-11-01", catalog);
		saveItem(fridge, "매일우유", "🍼", null, "2026-11-08", catalog);
		saveItem(fridge, "  김치 ", null, null, "2026-11-02", null);
		saveItem(fridge, "김치", null, null, "2026-11-12", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[?(@.name=='우유')].purchaseCount").value(2))
			.andExpect(jsonPath("$.data.items[?(@.name=='우유')].cycleDays").value(7))
			// 가장 최근에 넣은 재료의 이모지
			.andExpect(jsonPath("$.data.items[?(@.name=='우유')].emoji").value("🍼"))
			.andExpect(jsonPath("$.data.items[?(@.name=='김치')].purchaseCount").value(2))
			.andExpect(jsonPath("$.data.items[?(@.name=='김치')].cycleDays").value(10));
	}

	@Test
	void 구매일이_가장_늦고_같으면_id가_큰_재료의_이모지를_쓴다_없으면_null이다() throws Exception {
		// id는 작지만 구매일이 늦은 행이 이긴다
		saveItem(fridge, "사과", "🍎", null, "2026-11-10", null);
		saveItem(fridge, "사과", "🍏", null, "2026-11-01", null);
		// 구매일이 같으면 id가 큰 행. 이모지가 없는 행이면 null
		saveItem(fridge, "배", "🍐", null, "2026-11-01", null);
		saveItem(fridge, "배", null, null, "2026-11-01", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items[?(@.name=='사과')].emoji").value("🍎"))
			.andExpect(jsonPath("$.data.items[?(@.name=='배')].emoji").isEmpty());
	}

	@Test
	void 구매일이_하나면_가장_최근_재료의_구매_주기를_쓰고_없거나_0이면_뺀다() throws Exception {
		saveItem(fridge, "쌀", null, 30, "2026-11-01", null);
		saveItem(fridge, "소금", null, null, "2026-11-01", null);
		saveItem(fridge, "후추", null, 0, "2026-11-01", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("쌀"))
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(1))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(30))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-12-01"));
	}

	@Test
	void 구매일이_하나여도_가장_최근_재료에_주기가_없으면_예전_재료의_주기는_쓰지_않고_뺀다() throws Exception {
		saveItem(fridge, "쌀", null, 30, "2026-11-01", null);
		saveItem(fridge, "쌀", null, null, "2026-11-01", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 구매일이_둘_이상이면_구매_주기_값과_상관없이_구매일로_계산하고_최소_1일이다() throws Exception {
		// 간격 1일 / 3번 -> 0.33 -> 반올림 0이지만 최소 1일
		saveItem(fridge, "빵", null, 99, "2026-11-01", null);
		saveItem(fridge, "빵", null, 99, "2026-11-01", null);
		saveItem(fridge, "빵", null, 99, "2026-11-02", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items[0].purchaseCount").value(2))
			.andExpect(jsonPath("$.data.items[0].cycleDays").value(1))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-11-03"));
	}

	@Test
	void 구매일이_없는_재료는_뺀다() throws Exception {
		saveItem(fridge, "김", null, 10, null, null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 지난_날짜도_그대로_주고_다음_구매일_이름_순으로_정렬한다() throws Exception {
		saveItem(fridge, "당근", null, 5, "2026-11-10", null);
		saveItem(fridge, "가지", null, 5, "2026-11-10", null);
		saveItem(fridge, "라면", null, 1, "2026-01-01", null);
		saveItem(fridge, "우유", null, 3, "2026-11-10", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(4))
			.andExpect(jsonPath("$.data.items[0].name").value("라면"))
			.andExpect(jsonPath("$.data.items[0].nextPurchaseDate").value("2026-01-02"))
			.andExpect(jsonPath("$.data.items[1].name").value("우유"))
			.andExpect(jsonPath("$.data.items[2].name").value("가지"))
			.andExpect(jsonPath("$.data.items[3].name").value("당근"));
	}

	@Test
	void 다른_냉장고의_재료는_뺀다() throws Exception {
		Fridge other = em.merge(new Fridge("남의 냉장고"));
		saveItem(other, "우유", null, 7, "2026-11-01", null);
		saveItem(fridge, "두부", null, 7, "2026-11-01", null);

		getSchedule(fridge.getId(), memberToken)
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].name").value("두부"));
	}

	@Test
	void 재료가_없으면_빈_목록을_준다() throws Exception {
		getSchedule(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 멤버가_아니거나_없는_냉장고면_404를_반환한다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));

		getSchedule(fridge.getId(), jwtProvider.createAccessToken(stranger.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		getSchedule(Long.MAX_VALUE, memberToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/purchase-schedule", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getSchedule(Long fridgeId, String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/purchase-schedule", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private FridgeItem saveItem(Fridge target, String name, String emoji, Integer cycleDays, String purchasedAt,
		IngredientCatalog catalog) {
		FridgeItem item = FridgeItem.builder()
			.fridge(target)
			.catalog(catalog)
			.name(name)
			.emoji(emoji)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.purchasedAt(purchasedAt == null ? null : LocalDate.parse(purchasedAt))
			.purchaseCycleDays(cycleDays)
			.build();
		em.persist(item);
		return item;
	}

}
