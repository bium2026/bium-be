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

import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 이메일로_가입하고_대소문자와_앞뒤_공백에_상관없이_그_이메일로_로그인한다() throws Exception {
		signup("{\"email\": \" New@Bium.test \", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.userId").isNumber());
		assertThat(userRepository.existsByEmail("new@bium.test")).isTrue();

		String body = login("{\"email\": \"NEW@bium.test \", \"password\": \"bium2026\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andReturn().getResponse().getContentAsString();

		// 알림 설정을 저장한 적이 없으면 가입한 이메일을 받을 주소로 보여 준다
		String token = JsonPath.read(body, "$.data.accessToken");
		mockMvc.perform(get("/api/notification-settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("new@bium.test"));
	}

	@Test
	void 이미_가입한_이메일이면_대소문자가_달라도_409를_반환한다() throws Exception {
		signup("{\"email\": \"new@bium.test\", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isCreated());

		signup("{\"email\": \"NEW@bium.test\", \"password\": \"bium2027\", \"nickname\": \"선재\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"))
			.andExpect(jsonPath("$.error.message").value("이미 가입한 이메일입니다."));
	}

	@Test
	void 이메일이_없거나_비어_있으면_가입하지_않는다() throws Exception {
		signup("{\"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요."));
		signup("{\"email\": \"   \", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요."));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void 닉네임의_앞뒤_공백을_지우고_공백만_넣으면_가입하지_않는다() throws Exception {
		signup("{\"email\": \"new@bium.test\", \"password\": \"bium2026\", \"nickname\": \"  강희\u3000\"}")
			.andExpect(status().isCreated());
		assertThat(userRepository.findAllByEmail("new@bium.test")).extracting(User::getNickname)
			.containsExactly("강희");

		signup("{\"email\": \"other@bium.test\", \"password\": \"bium2026\", \"nickname\": \"    \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("닉네임을 입력해 주세요."));
		signup("{\"email\": \"other@bium.test\", \"password\": \"bium2026\", \"nickname\": \" 강 \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("닉네임은 2~10자여야 합니다."));

		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void 이메일_형식이_틀리면_가입하지_않는다() throws Exception {
		signup("{\"email\": \"not-an-email\", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("이메일 형식이 올바르지 않습니다."));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void 비밀번호가_틀리거나_가입하지_않은_이메일이면_같은_문구로_401을_반환한다() throws Exception {
		signup("{\"email\": \"new@bium.test\", \"password\": \"bium2026\", \"nickname\": \"강희\"}")
			.andExpect(status().isCreated());

		login("{\"email\": \"new@bium.test\", \"password\": \"wrong2026\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
			.andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 일치하지 않습니다."));
		login("{\"email\": \"nobody@bium.test\", \"password\": \"bium2026\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 일치하지 않습니다."));
	}

	@Test
	void 로그인할_때_이메일이_없으면_400을_반환한다() throws Exception {
		login("{\"password\": \"bium2026\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요."));
	}

	private ResultActions signup(String body) throws Exception {
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
