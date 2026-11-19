package com.bium.domain.fridge.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FridgeShoppingDayControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private String memberToken;
	private String strangerToken;

	@BeforeEach
	void setUp() {
		User owner = em.merge(new User("owner", "password", "주인"));
		User member = em.merge(new User("member", "password", "멤버"));
		User stranger = em.merge(new User("stranger", "password", "남"));
		fridge = em.merge(new Fridge("우리 집 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 멤버는_장보는_요일을_정하고_지울_수_있고_냉장고_목록에도_보인다() throws Exception {
		change(memberToken, "{\"shoppingDay\": \"SATURDAY\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(fridge.getId()))
			.andExpect(jsonPath("$.data.role").value("MEMBER"))
			.andExpect(jsonPath("$.data.shoppingDay").value("SATURDAY"));
		mockMvc.perform(get("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(jsonPath("$.data[0].shoppingDay").value("SATURDAY"));

		change(memberToken, "{\"shoppingDay\": null}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shoppingDay").doesNotExist());
	}

	@Test
	void 멤버가_아니면_404_없는_요일이면_400을_반환한다() throws Exception {
		change(strangerToken, "{\"shoppingDay\": \"SATURDAY\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		change(memberToken, "{\"shoppingDay\": \"FUNDAY\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 재료_응답에_넣은_날을_담는다() throws Exception {
		em.persist(FridgeItem.builder().fridge(fridge).name("우유").quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE).section(SectionType.CENTER).sortOrder(0)
			.purchasedAt(LocalDate.of(2026, 11, 10)).expiresAt(LocalDate.of(2026, 11, 20)).build());

		mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.center[0].purchasedAt").value("2026-11-10"))
			.andExpect(jsonPath("$.data.fridge.center[0].expiresAt").value("2026-11-20"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(patch("/api/fridges/{fridgeId}/shopping-day", fridge.getId())
				.contentType(MediaType.APPLICATION_JSON).content("{\"shoppingDay\": \"SATURDAY\"}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions change(String token, String body) throws Exception {
		return mockMvc.perform(patch("/api/fridges/{fridgeId}/shopping-day", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
