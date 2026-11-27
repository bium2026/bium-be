package com.bium.domain.fridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	@Test
	void 속한_냉장고가_없으면_새_냉장고를_만들고_주인이_된다() throws Exception {
		createFridge(myToken, "{\"name\": \" 새 냉장고 \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.name").value("새 냉장고"))
			.andExpect(jsonPath("$.data.role").value("OWNER"))
			.andExpect(jsonPath("$.data.isPublic").value(false));

		getMyFridges(myToken)
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].name").value("새 냉장고"))
			.andExpect(jsonPath("$.data[0].role").value("OWNER"));
	}

	@Test
	void 속한_냉장고가_있으면_새_냉장고를_만들지_않는다() throws Exception {
		Fridge shared = em.merge(new Fridge("자취방 냉장고"));
		em.persist(new FridgeMember(shared, me, MemberRole.MEMBER));

		createFridge(myToken, "{\"name\": \"새 냉장고\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ALREADY_EXISTS"));
		getMyFridges(myToken).andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void 냉장고_이름은_비우거나_50자를_넘길_수_없다() throws Exception {
		createFridge(myToken, "{\"name\": \"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("냉장고 이름을 입력해 주세요."));
		createFridge(myToken, "{\"name\": \"" + "가".repeat(51) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("냉장고 이름은 50자까지 쓸 수 있습니다."));
		getMyFridges(myToken).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 주인은_냉장고_이름을_바꾼다() throws Exception {
		Fridge mine = em.merge(new Fridge("우리 집 냉장고"));
		em.persist(new FridgeMember(mine, me, MemberRole.OWNER));

		rename(mine.getId(), myToken, "{\"name\": \" 본가 냉장고 \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(mine.getId()))
			.andExpect(jsonPath("$.data.name").value("본가 냉장고"))
			.andExpect(jsonPath("$.data.role").value("OWNER"));

		em.flush();
		em.clear();
		assertThat(em.find(Fridge.class, mine.getId()).getName()).isEqualTo("본가 냉장고");
	}

	@Test
	void 멤버는_403_남은_404로_냉장고_이름을_바꾸지_못한다() throws Exception {
		User owner = em.merge(new User("owner", "password", "주인"));
		Fridge shared = em.merge(new Fridge("자취방 냉장고"));
		em.persist(new FridgeMember(shared, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(shared, me, MemberRole.MEMBER));
		User stranger = em.merge(new User("stranger", "password", "남"));

		rename(shared.getId(), myToken, "{\"name\": \"내 냉장고\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_OWNER_ONLY"));
		rename(shared.getId(), jwtProvider.createAccessToken(stranger.getId()), "{\"name\": \"내 냉장고\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		rename(shared.getId(), jwtProvider.createAccessToken(owner.getId()), "{\"name\": \"\"}")
			.andExpect(status().isBadRequest());

		em.flush();
		em.clear();
		assertThat(em.find(Fridge.class, shared.getId()).getName()).isEqualTo("자취방 냉장고");
	}

	private ResultActions createFridge(String token, String body) throws Exception {
		return mockMvc.perform(post("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions rename(Long fridgeId, String token, String body) throws Exception {
		return mockMvc.perform(patch("/api/fridges/{fridgeId}/name", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions getMyFridges(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
