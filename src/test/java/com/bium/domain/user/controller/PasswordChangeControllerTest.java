package com.bium.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.repository.RefreshTokenRepository;
import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.auth.service.RefreshTokenService;
import com.bium.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private User user;
	private String token;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("kanghee@bium.test", passwordEncoder.encode("bium2026"), "강희"));
		token = jwtProvider.createAccessToken(user.getId());
	}

	@Test
	void 비밀번호를_바꾸면_새_비밀번호로만_로그인되고_다른_기기의_리프레시_토큰은_모두_끊긴다() throws Exception {
		String otherDeviceToken = refreshTokenOf(login("bium2026").andExpect(status().isOk()));

		String body = changePassword("{\"currentPassword\": \"bium2026\", \"newPassword\": \"newpass99\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andExpect(jsonPath("$.data.expiresIn").value(3600))
			.andExpect(jsonPath("$.data.refreshToken").isString())
			.andReturn().getResponse().getContentAsString();
		String thisDeviceToken = JsonPath.read(body, "$.data.refreshToken");

		// 남은 리프레시 토큰은 지금 기기에 새로 준 것 하나뿐이다
		assertThat(refreshTokenRepository.findAll()).singleElement()
			.satisfies(saved -> assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(thisDeviceToken)));
		refresh(otherDeviceToken).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
		refresh(thisDeviceToken).andExpect(status().isOk());

		login("bium2026").andExpect(status().isUnauthorized());
		login("newpass99").andExpect(status().isOk());
		em.flush();
		em.clear();
		assertThat(passwordEncoder.matches("newpass99", em.find(User.class, user.getId()).getPassword())).isTrue();
	}

	@Test
	void 지금_비밀번호가_틀리면_400으로_알리고_바꾸지_않는다() throws Exception {
		String refreshToken = refreshTokenOf(login("bium2026"));

		// 401이면 화면이 로그인 만료로 보고 토큰을 지우므로 400으로 알린다
		changePassword("{\"currentPassword\": \"wrong2026\", \"newPassword\": \"newpass99\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("WRONG_PASSWORD"))
			.andExpect(jsonPath("$.error.message").value("비밀번호가 일치하지 않습니다."));

		login("bium2026").andExpect(status().isOk());
		refresh(refreshToken).andExpect(status().isOk());
	}

	@Test
	void 새_비밀번호는_가입_때와_같은_규칙으로_보고_지금과_같으면_막는다() throws Exception {
		changePassword("{\"currentPassword\": \"bium2026\", \"newPassword\": \"short1\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("비밀번호는 공백 없이 영문과 숫자를 포함한 8~20자여야 합니다."));
		changePassword("{\"currentPassword\": \"bium2026\", \"newPassword\": \"onlyletters\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		changePassword("{\"currentPassword\": \"\", \"newPassword\": \"newpass99\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("지금 비밀번호를 입력해 주세요."));
		changePassword("{\"currentPassword\": \"bium2026\", \"newPassword\": \"bium2026\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("SAME_PASSWORD"));

		login("bium2026").andExpect(status().isOk());
	}

	@Test
	void 로그인하지_않으면_비밀번호를_바꾸지_않는다() throws Exception {
		mockMvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\": \"bium2026\", \"newPassword\": \"newpass99\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions changePassword(String body) throws Exception {
		return mockMvc.perform(patch("/api/users/me/password")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"kanghee@bium.test\", \"password\": \"" + password + "\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\": \"" + refreshToken + "\"}"));
	}

	private static String refreshTokenOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.refreshToken");
	}

}
