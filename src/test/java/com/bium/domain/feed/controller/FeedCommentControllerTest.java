package com.bium.domain.feed.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.feed.entity.FeedComment;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedCommentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User viewer;
	private Fridge fridge;
	private String viewerToken;
	private String strangerToken;

	@BeforeEach
	void setUp() {
		User owner = em.merge(new User("owner", "password", "강희"));
		viewer = em.merge(new User("viewer", "password", "윤호"));
		User stranger = em.merge(new User("stranger", "password", "도윤"));
		fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		fridge.changeVisibility(true);
		viewerToken = jwtProvider.createAccessToken(viewer.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 코멘트를_남기면_201과_남긴_코멘트를_준다() throws Exception {
		create("{\"content\":\"두부가 많네요\",\"dishName\":\"마파두부\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.content").value("두부가 많네요"))
			.andExpect(jsonPath("$.data.dishName").value("마파두부"))
			.andExpect(jsonPath("$.data.authorNickname").value("윤호"));
	}

	@Test
	void 코멘트가_비었거나_너무_길거나_음식_이름에_줄바꿈이_있으면_400을_준다() throws Exception {
		create("{\"content\":\"  \"}").andExpect(status().isBadRequest());
		create("{\"content\":\"" + "가".repeat(201) + "\"}").andExpect(status().isBadRequest());
		create("{\"content\":\"좋아요\",\"dishName\":\"마파\\n두부\"}").andExpect(status().isBadRequest());
	}

	@Test
	void 로그인하지_않으면_401을_준다() throws Exception {
		mockMvc.perform(get("/api/feed/fridges/{fridgeId}/comments", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 코멘트_목록을_주고_피드_카드에도_코멘트_수를_준다() throws Exception {
		em.persist(new FeedComment(fridge, viewer, "구경 잘 했어요", null));

		mockMvc.perform(get("/api/feed/fridges/{fridgeId}/comments", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.comments[0].content").value("구경 잘 했어요"))
			.andExpect(jsonPath("$.data.comments[0].mine").value(false))
			.andExpect(jsonPath("$.data.nextCursor").isEmpty());
		mockMvc.perform(get("/api/feed/fridges")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridges[0].commentCount").value(1));
	}

	@Test
	void 남의_코멘트를_지우면_403_내_코멘트를_지우면_204를_준다() throws Exception {
		FeedComment comment = new FeedComment(fridge, viewer, "구경 잘 했어요", null);
		em.persist(comment);

		mockMvc.perform(delete("/api/feed/fridges/{fridgeId}/comments/{commentId}", fridge.getId(), comment.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FEED_COMMENT_DELETE_FORBIDDEN"));
		mockMvc.perform(delete("/api/feed/fridges/{fridgeId}/comments/{commentId}", fridge.getId(), comment.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/feed/fridges/{fridgeId}/comments/{commentId}", fridge.getId(), comment.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
			.andExpect(status().isNotFound());
	}

	@Test
	void 내_코멘트를_고치면_200과_고친_코멘트를_주고_쓸_때와_같게_검사한다() throws Exception {
		FeedComment comment = new FeedComment(fridge, viewer, "구경 잘 했어요", null);
		em.persist(comment);

		update(comment, viewerToken, "{\"content\":\"두부가 많네요\",\"dishName\":\"마파두부\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.commentId").value(comment.getId()))
			.andExpect(jsonPath("$.data.content").value("두부가 많네요"))
			.andExpect(jsonPath("$.data.dishName").value("마파두부"));
		update(comment, viewerToken, "{\"content\":\"  \"}").andExpect(status().isBadRequest());
		update(comment, viewerToken, "{\"content\":\"" + "가".repeat(201) + "\"}").andExpect(status().isBadRequest());
		update(comment, viewerToken, "{\"content\":\"좋아요\",\"dishName\":\"마파\\n두부\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_코멘트를_고치면_403을_준다() throws Exception {
		FeedComment comment = new FeedComment(fridge, viewer, "구경 잘 했어요", null);
		em.persist(comment);

		update(comment, strangerToken, "{\"content\":\"바꿨어요\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FEED_COMMENT_EDIT_FORBIDDEN"));
	}

	private ResultActions update(FeedComment comment, String token, String body) throws Exception {
		return mockMvc.perform(put("/api/feed/fridges/{fridgeId}/comments/{commentId}", fridge.getId(), comment.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/feed/fridges/{fridgeId}/comments", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
