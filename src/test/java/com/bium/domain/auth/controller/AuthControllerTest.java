package com.bium.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	void 가입하면_앞뒤_공백을_뺀_이메일을_저장하고_알림_설정에서_보여_준다() throws Exception {
		signup("{\"loginId\": \"newuser\", \"password\": \"bium2026\", \"nickname\": \"강희\", \"email\": \" new@bium.test \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.userId").isNumber());

		User user = userRepository.findByLoginId("newuser").orElseThrow();
		assertThat(user.getEmail()).isEqualTo("new@bium.test");

		mockMvc.perform(get("/api/notification-settings")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("new@bium.test"));
	}

	@Test
	void 이메일이_없거나_비어_있으면_가입하지_않는다() throws Exception {
		signup("{\"loginId\": \"newuser\", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요."));
		signup("{\"loginId\": \"newuser\", \"password\": \"bium2026\", \"nickname\": \"강희\", \"email\": \"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요."));

		assertThat(userRepository.existsByLoginId("newuser")).isFalse();
	}

	@Test
	void 이메일_형식이_틀리면_가입하지_않는다() throws Exception {
		signup("{\"loginId\": \"newuser\", \"password\": \"bium2026\", \"nickname\": \"강희\", \"email\": \"not-an-email\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("이메일 형식이 올바르지 않습니다."));

		assertThat(userRepository.existsByLoginId("newuser")).isFalse();
	}

	private ResultActions signup(String body) throws Exception {
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
