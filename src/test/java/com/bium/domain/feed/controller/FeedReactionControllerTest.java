package com.bium.domain.feed.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.feed.entity.FeedLike;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedReactionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User owner;
	private Fridge fridge;
	private String viewerToken;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "강희"));
		User viewer = em.merge(new User("viewer", "password", "윤호"));
		fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		fridge.changeVisibility(true);
		viewerToken = jwtProvider.createAccessToken(viewer.getId());
	}

	@Test
	void 좋아요를_누르면_좋아요_수가_오르고_여러_번_눌러도_한_번만_센다() throws Exception {
		em.persist(new FeedLike(owner, fridge));

		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(2))
			.andExpect(jsonPath("$.data.liked").value(true))
			.andExpect(jsonPath("$.data.saved").value(false));
		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(2));
	}

	@Test
	void 좋아요를_취소하면_좋아요_수가_내려가고_다시_취소해도_그대로다() throws Exception {
		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()));

		react(delete("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(0))
			.andExpect(jsonPath("$.data.liked").value(false));
		react(delete("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(0));
	}

	@Test
	void 냉장고를_저장하고_저장을_취소할_수_있다() throws Exception {
		react(put("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saved").value(true))
			.andExpect(jsonPath("$.data.liked").value(false));
		react(put("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(jsonPath("$.data.saved").value(true));

		react(delete("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saved").value(false));
		react(delete("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(jsonPath("$.data.saved").value(false));
	}

	@Test
	void 공개하지_않은_냉장고에는_좋아요나_저장을_누를_수_없다() throws Exception {
		fridge.changeVisibility(false);

		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		react(put("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(status().isNotFound());
		react(put("/api/feed/fridges/{fridgeId}/like", 999_999L))
			.andExpect(status().isNotFound());
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions react(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken));
	}

}
