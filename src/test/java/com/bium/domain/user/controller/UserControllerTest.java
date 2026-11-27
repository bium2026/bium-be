package com.bium.domain.user.controller;

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
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User user;
	private String token;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("kanghee@bium.test", "password", "강희"));
		token = jwtProvider.createAccessToken(user.getId());
	}

	@Test
	void 내_이메일과_닉네임을_보여_준다() throws Exception {
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("kanghee@bium.test"))
			.andExpect(jsonPath("$.data.nickname").value("강희"));
	}

	@Test
	void 로그인하지_않으면_내_정보를_보여_주지_않는다() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 닉네임의_앞뒤_공백을_지우고_바꾸며_냉장고_이름은_그대로_둔다() throws Exception {
		Fridge fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, user, MemberRole.OWNER));

		changeNickname("{\"nickname\": \"  선재　\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("선재"))
			.andExpect(jsonPath("$.data.email").value("kanghee@bium.test"));

		em.flush();
		em.clear();
		assertThat(em.find(User.class, user.getId()).getNickname()).isEqualTo("선재");
		assertThat(em.find(Fridge.class, fridge.getId()).getName()).isEqualTo("강희의 냉장고");
	}

	@Test
	void 가입_때와_같이_빈_닉네임과_길이가_맞지_않는_닉네임은_막는다() throws Exception {
		changeNickname("{\"nickname\": \"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("닉네임을 입력해 주세요."));
		changeNickname("{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("닉네임을 입력해 주세요."));
		changeNickname("{\"nickname\": \" 강 \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("닉네임은 2~10자여야 합니다."));
		changeNickname("{\"nickname\": \"열한글자닉네임입니다요\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("닉네임은 2~10자여야 합니다."));

		em.flush();
		em.clear();
		assertThat(em.find(User.class, user.getId()).getNickname()).isEqualTo("강희");
	}

	private ResultActions changeNickname(String body) throws Exception {
		return mockMvc.perform(patch("/api/users/me/nickname")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
