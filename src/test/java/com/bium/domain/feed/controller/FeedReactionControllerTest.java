package com.bium.domain.feed.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.feed.entity.FeedLike;
import com.bium.domain.feed.entity.FeedSave;
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
	private User member;
	// 이 냉장고 멤버가 아닌 다른 사람. 좋아요 수에 들어가는 남의 좋아요를 미리 넣을 때 쓴다
	private User stranger;
	private Fridge fridge;
	private String viewerToken;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "강희"));
		member = em.merge(new User("member", "password", "선재"));
		stranger = em.merge(new User("stranger", "password", "도윤"));
		User viewer = em.merge(new User("viewer", "password", "윤호"));
		fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		fridge.changeVisibility(true);
		viewerToken = jwtProvider.createAccessToken(viewer.getId());
	}

	@Test
	void 좋아요를_누르면_좋아요_수가_오르고_여러_번_눌러도_한_번만_센다() throws Exception {
		em.persist(new FeedLike(stranger, fridge));

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
	void 냉장고가_비공개로_바뀌어도_좋아요와_저장을_취소할_수_있고_다시_취소해도_그대로다() throws Exception {
		em.persist(new FeedLike(stranger, fridge));
		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()));
		react(put("/api/feed/fridges/{fridgeId}/save", fridge.getId()));
		fridge.changeVisibility(false);

		react(delete("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(false))
			.andExpect(jsonPath("$.data.saved").value(true));
		react(delete("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saved").value(false));
		react(delete("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(false));
		react(delete("/api/feed/fridges/{fridgeId}/save", fridge.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saved").value(false));
	}

	@Test
	void 주인과_멤버는_자기_냉장고에_좋아요나_저장을_누를_수_없고_기록도_남지_않는다() throws Exception {
		for (User user : new User[] {owner, member}) {
			String token = jwtProvider.createAccessToken(user.getId());
			react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()), token)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("OWN_FRIDGE_REACTION_FORBIDDEN"));
			react(put("/api/feed/fridges/{fridgeId}/save", fridge.getId()), token)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("OWN_FRIDGE_REACTION_FORBIDDEN"));
		}
		// 공개하지 않은 냉장고도 멤버에게는 막은 이유를 알려 준다
		fridge.changeVisibility(false);
		react(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()), jwtProvider.createAccessToken(member.getId()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("OWN_FRIDGE_REACTION_FORBIDDEN"));

		assertThat(countRows("FeedLike")).isZero();
		assertThat(countRows("FeedSave")).isZero();
	}

	@Test
	void 자기_냉장고에_남은_좋아요와_저장은_수와_내_여부에서_빼고_취소로_지울_수_있다() throws Exception {
		em.persist(new FeedLike(owner, fridge));
		em.persist(new FeedLike(member, fridge));
		em.persist(new FeedSave(member, fridge));
		em.persist(new FeedLike(stranger, fridge));
		String memberToken = jwtProvider.createAccessToken(member.getId());

		react(delete("/api/feed/fridges/{fridgeId}/like", fridge.getId()), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(false))
			.andExpect(jsonPath("$.data.saved").value(false));
		react(delete("/api/feed/fridges/{fridgeId}/save", fridge.getId()), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saved").value(false));

		// 멤버 기록만 지우고, 주인이 남긴 기록과 남의 좋아요는 그대로 둔다
		assertThat(countRows("FeedLike")).isEqualTo(2);
		assertThat(countRows("FeedSave")).isZero();
	}

	@Test
	void 주인과_멤버도_자기_냉장고에_코멘트는_남길_수_있다() throws Exception {
		mockMvc.perform(post("/api/feed/fridges/{fridgeId}/comments", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(owner.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"이번 주에 두부 먹어요\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(put("/api/feed/fridges/{fridgeId}/like", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions react(MockHttpServletRequestBuilder request) throws Exception {
		return react(request, viewerToken);
	}

	private ResultActions react(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private long countRows(String entity) {
		return em.createQuery("select count(r) from " + entity + " r where r.fridge.id = :fridgeId", Long.class)
			.setParameter("fridgeId", fridge.getId())
			.getSingleResult();
	}

}
