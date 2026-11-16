package com.bium.global.kamis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KamisClientTest {

	private static final String BASE_URL = "https://kamis.test";
	private static final LocalDate REG_DAY = LocalDate.of(2026, 11, 10);

	private MockRestServiceServer server;
	private KamisClient kamisClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		kamisClient = new KamisClient(builder.build(), properties("key", "id"));
	}

	@Test
	void 소매가를_요청하고_쉼표를_뺀_가격으로_읽는다() {
		server.expect(requestTo(startsWith(BASE_URL + "/service/price/xml.do")))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("action", "dailyPriceByCategoryList"))
			.andExpect(queryParam("p_product_cls_code", "01"))
			.andExpect(queryParam("p_item_category_code", "200"))
			.andExpect(queryParam("p_regday", "2026-11-10"))
			.andExpect(queryParam("p_returntype", "json"))
			.andExpect(queryParam("p_cert_key", "key"))
			.andExpect(queryParam("p_cert_id", "id"))
			// KAMIS는 JSON을 text/plain으로 보낸다
			.andRespond(withSuccess("""
				{"condition": [], "data": {"error_code": "000", "item": [
				  {"item_name": "양파", "kind_name": "양파(1kg)", "rank": "상품", "unit": "1kg", "dpr1": "3,450", "dpr3": "3,600"},
				  {"item_name": "대파", "kind_name": "대파(1kg)", "rank": "상품", "unit": "1kg", "dpr1": "-", "dpr3": "2,980"}
				]}}
				""", MediaType.TEXT_PLAIN));

		assertThat(kamisClient.fetchRetailPrices("200", REG_DAY)).containsExactly(
			new KamisPrice("양파", "양파(1kg)", "상품", "1kg", 3450, 3600),
			new KamisPrice("대파", "대파(1kg)", "상품", "1kg", null, 2980));
		server.verify();
	}

	@Test
	void 품목이_하나면_배열이_아닌_객체로_와도_읽는다() {
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withSuccess("""
			{"data": {"error_code": "000", "item": {"item_name": "쌀", "kind_name": "20kg", "rank": "상품", "unit": "20kg", "dpr1": "58,000", "dpr3": ""}}}
			""", MediaType.TEXT_PLAIN));

		assertThat(kamisClient.fetchRetailPrices("100", REG_DAY))
			.containsExactly(new KamisPrice("쌀", "20kg", "상품", "20kg", 58000, null));
	}

	@Test
	void 결과_코드만_온_실패_응답은_빈_목록이다() {
		server.expect(requestTo(startsWith(BASE_URL)))
			.andRespond(withSuccess("{\"condition\": [], \"data\": [\"200\"]}", MediaType.TEXT_PLAIN));

		assertThat(kamisClient.fetchRetailPrices("200", REG_DAY)).isEmpty();
	}

	@Test
	void JSON이_아닌_응답은_빈_목록이다() {
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withSuccess("<html>점검 중</html>", MediaType.TEXT_HTML));

		assertThat(kamisClient.fetchRetailPrices("200", REG_DAY)).isEmpty();
	}

	@Test
	void 서버_오류는_빈_목록이다() {
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());

		assertThat(kamisClient.fetchRetailPrices("200", REG_DAY)).isEmpty();
	}

	@Test
	void 인증값이_없으면_호출하지_않는다() {
		KamisClient clientWithoutKey = new KamisClient(RestClient.create(BASE_URL), properties("", "id"));

		assertThat(clientWithoutKey.fetchRetailPrices("200", REG_DAY)).isEmpty();
	}

	@Test
	void 숫자가_아니거나_범위를_벗어난_가격은_null이다() {
		assertThat(KamisClient.parsePrice("1,234")).isEqualTo(1234);
		assertThat(KamisClient.parsePrice("-")).isNull();
		assertThat(KamisClient.parsePrice("")).isNull();
		assertThat(KamisClient.parsePrice("0")).isNull();
		assertThat(KamisClient.parsePrice("12.5")).isNull();
		assertThat(KamisClient.parsePrice("100,000,000")).isNull();
	}

	private KamisProperties properties(String certKey, String certId) {
		return new KamisProperties(certKey, certId, BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

}
