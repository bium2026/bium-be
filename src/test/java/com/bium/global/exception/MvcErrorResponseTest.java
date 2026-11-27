package com.bium.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * Spring MVC가 컨트롤러에 닿기 전에 막는 요청도 공통 응답 형식과 한국어 문구로 알린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MvcErrorResponseTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private String token;

	@BeforeEach
	void setUp() {
		User user = em.merge(new User("mvc-error", "password", "멤버"));
		token = jwtProvider.createAccessToken(user.getId());
	}

	@Test
	void 형식이_틀린_요청_본문은_INVALID_INPUT과_한국어로_알린다() throws Exception {
		perform(put("/api/fridge-items/{itemId}", 1).contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
	}

	@Test
	void 숫자_자리에_글자를_보내거나_필요한_값을_빠뜨려도_INVALID_INPUT으로_알린다() throws Exception {
		perform(get("/api/fridges/{fridgeId}/items", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		perform(delete("/api/fridge-items/{itemId}", 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
	}

	@Test
	void 없는_주소와_지원하지_않는_메서드도_한국어로_알린다() throws Exception {
		perform(get("/api/no-such-path"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("요청한 주소를 찾을 수 없습니다."));
		perform(put("/api/fridges"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않는 요청 방식입니다."));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
