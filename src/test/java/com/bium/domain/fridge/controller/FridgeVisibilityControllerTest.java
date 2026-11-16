package com.bium.domain.fridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class FridgeVisibilityControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private String ownerToken;
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
		ownerToken = jwtProvider.createAccessToken(owner.getId());
		memberToken = jwtProvider.createAccessToken(member.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 처음_만든_냉장고는_공개하지_않은_상태다() throws Exception {
		mockMvc.perform(get("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].isPublic").value(false));
	}

	@Test
	void 소유자는_냉장고를_피드에_공개하고_다시_숨길_수_있다() throws Exception {
		changeVisibility(ownerToken, "{\"isPublic\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(fridge.getId()))
			.andExpect(jsonPath("$.data.name").value("우리 집 냉장고"))
			.andExpect(jsonPath("$.data.role").value("OWNER"))
			.andExpect(jsonPath("$.data.isPublic").value(true));
		assertThat(reload().isPublic()).isTrue();

		// 냉장고 목록에서도 공개 여부를 알려 준다
		mockMvc.perform(get("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(jsonPath("$.data[0].isPublic").value(true));

		changeVisibility(ownerToken, "{\"isPublic\": false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isPublic").value(false));
		assertThat(reload().isPublic()).isFalse();
	}

	@Test
	void 멤버는_공개_여부를_바꿀_수_없다() throws Exception {
		changeVisibility(memberToken, "{\"isPublic\": true}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_OWNER_ONLY"));
		assertThat(reload().isPublic()).isFalse();
	}

	@Test
	void 멤버가_아니면_냉장고가_없을_때와_같은_404를_반환한다() throws Exception {
		changeVisibility(strangerToken, "{\"isPublic\": true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		assertThat(reload().isPublic()).isFalse();
	}

	@Test
	void 공개_여부를_보내지_않으면_400을_반환한다() throws Exception {
		changeVisibility(ownerToken, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("공개 여부를 보내 주세요."));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(patch("/api/fridges/{fridgeId}/visibility", fridge.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"isPublic\": true}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions changeVisibility(String token, String body) throws Exception {
		return mockMvc.perform(patch("/api/fridges/{fridgeId}/visibility", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private Fridge reload() {
		em.flush();
		em.clear();
		return em.find(Fridge.class, fridge.getId());
	}

}
