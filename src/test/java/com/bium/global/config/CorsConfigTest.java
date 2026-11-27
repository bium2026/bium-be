package com.bium.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 허용된_주소의_사전_요청은_통과한다() throws Exception {
		mockMvc.perform(options("/api/fridges")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void 로컬의_다른_포트와_ngrok_주소도_통과한다() throws Exception {
		for (String origin : new String[] {"http://localhost:4173", "https://ab12-34-56.ngrok-free.app",
			"https://bium.ngrok.app", "https://bium-fe.ngrok-free.dev", "https://bium.ngrok.dev"}) {
			mockMvc.perform(options("/api/fridges")
					.header(HttpHeaders.ORIGIN, origin)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,ngrok-skip-browser-warning"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
		}
	}

	@Test
	void ngrok을_흉내_낸_주소나_http_ngrok_주소는_거부한다() throws Exception {
		for (String origin : new String[] {"https://ngrok-free.app.evil.com", "https://evilngrok-free.app",
			"http://ab12.ngrok-free.app", "https://ngrok-free.dev.evil.com", "http://ab12.ngrok-free.dev"}) {
			mockMvc.perform(options("/api/fridges")
					.header(HttpHeaders.ORIGIN, origin)
					.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		}
	}

	@Test
	void 허용되지_않은_주소의_사전_요청은_거부한다() throws Exception {
		mockMvc.perform(options("/api/fridges")
				.header(HttpHeaders.ORIGIN, "http://evil.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void 인증_없이_보호된_API를_요청하면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
	}

}
