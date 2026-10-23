package com.bium.domain.fridge.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FridgeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User me;
	private String myToken;

	@BeforeEach
	void setUp() {
		me = em.merge(new User("me", "password", "나"));
		myToken = jwtProvider.createAccessToken(me.getId());
	}

	@Test
	void 내가_소유자이거나_멤버인_냉장고만_역할과_함께_조회한다() throws Exception {
		Fridge mine = em.merge(new Fridge("우리 집 냉장고"));
		Fridge shared = em.merge(new Fridge("자취방 냉장고"));
		Fridge others = em.merge(new Fridge("남의 냉장고"));
		User stranger = em.merge(new User("stranger", "password", "남"));
		em.persist(new FridgeMember(mine, me, MemberRole.OWNER));
		em.persist(new FridgeMember(shared, me, MemberRole.MEMBER));
		em.persist(new FridgeMember(others, stranger, MemberRole.OWNER));

		getMyFridges(myToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(mine.getId()))
			.andExpect(jsonPath("$.data[0].name").value("우리 집 냉장고"))
			.andExpect(jsonPath("$.data[0].role").value("OWNER"))
			.andExpect(jsonPath("$.data[1].id").value(shared.getId()))
			.andExpect(jsonPath("$.data[1].role").value("MEMBER"));
	}

	@Test
	void 속한_냉장고가_없으면_빈_목록을_반환한다() throws Exception {
		getMyFridges(myToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getMyFridges(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
