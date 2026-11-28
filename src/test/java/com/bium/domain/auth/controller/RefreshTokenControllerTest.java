package com.bium.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

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

import com.bium.domain.auth.entity.RefreshToken;
import com.bium.domain.auth.repository.RefreshTokenRepository;
import com.bium.domain.auth.service.RefreshTokenService;
import com.bium.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private Clock clock;

	private User user;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("kanghee@bium.test", passwordEncoder.encode("bium2026"), "강희"));
	}

	@Test
	void 로그인하면_리프레시_토큰을_주고_DB에는_해시만_저장한다() throws Exception {
		String body = login().andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andExpect(jsonPath("$.data.expiresIn").value(3600))
			.andExpect(jsonPath("$.data.refreshToken").isString())
			.andReturn().getResponse().getContentAsString();
		String refreshToken = JsonPath.read(body, "$.data.refreshToken");

		assertThat(refreshTokenRepository.findAll()).singleElement().satisfies(saved -> {
			assertThat(saved.getTokenHash()).isNotEqualTo(refreshToken).isEqualTo(RefreshTokenService.hash(refreshToken));
			assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now(clock).plusDays(13));
		});
	}

	@Test
	void 리프레시_토큰으로_새_토큰을_받고_쓴_토큰은_다시_쓸_수_없다() throws Exception {
		String refreshToken = JsonPath.read(login().andReturn().getResponse().getContentAsString(),
			"$.data.refreshToken");

		String body = refresh(refreshToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andExpect(jsonPath("$.data.expiresIn").value(3600))
			.andReturn().getResponse().getContentAsString();
		String newAccessToken = JsonPath.read(body, "$.data.accessToken");
		String newRefreshToken = JsonPath.read(body, "$.data.refreshToken");
		assertThat(newRefreshToken).isNotEqualTo(refreshToken);

		// 새 액세스 토큰으로 로그인해야 하는 API를 부를 수 있다
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("강희"));

		refresh(refreshToken).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
			.andExpect(jsonPath("$.error.message").value("로그인이 만료됐습니다. 다시 로그인해 주세요."));
		refresh(newRefreshToken).andExpect(status().isOk());
	}

	@Test
	void 만료되거나_모르는_리프레시_토큰은_401을_반환한다() throws Exception {
		em.persist(new RefreshToken(user, RefreshTokenService.hash("expired-token"), LocalDateTime.now(clock)));

		refresh("expired-token").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
		refresh("unknown-token").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void 만료된_액세스_토큰이_붙어_있어도_갱신한다() throws Exception {
		String refreshToken = JsonPath.read(login().andReturn().getResponse().getContentAsString(),
			"$.data.refreshToken");

		mockMvc.perform(post("/api/auth/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer expired.access.token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"" + refreshToken + "\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void 로그아웃하면_그_리프레시_토큰을_지우고_다시_로그아웃해도_성공한다() throws Exception {
		String refreshToken = JsonPath.read(login().andReturn().getResponse().getContentAsString(),
			"$.data.refreshToken");
		String otherDeviceToken = JsonPath.read(login().andReturn().getResponse().getContentAsString(),
			"$.data.refreshToken");

		logout(refreshToken).andExpect(status().isNoContent());
		logout(refreshToken).andExpect(status().isNoContent());

		refresh(refreshToken).andExpect(status().isUnauthorized());
		// 다른 기기의 로그인은 그대로 둔다
		refresh(otherDeviceToken).andExpect(status().isOk());
	}

	@Test
	void 리프레시_토큰이_없으면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \" \"}"))
			.andExpect(status().isBadRequest());
	}

	private ResultActions login() throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"kanghee@bium.test\", \"password\": \"bium2026\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\": \"" + refreshToken + "\"}"));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\": \"" + refreshToken + "\"}"));
	}

}
