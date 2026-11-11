package com.bium.global.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;
import com.bium.global.nutrition.FoodNutritionClient.Food;

class FoodNutritionClientTest {

	private static final String BASE_URL = "https://nutrition.test/1471000/FoodNtrCpntDbInfo03";
	private static final String ENDPOINT = BASE_URL + "/getFoodNtrCpntDbInq03?";

	private MockRestServiceServer server;
	private FoodNutritionClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new FoodNutritionClient(builder.build(), properties("ab+cd/ef=="));
	}

	@Test
	void 서비스_키와_검색어를_인코딩해_품목대표_식품을_JSON으로_찾는다() {
		server.expect(requestTo(startsWith(ENDPOINT)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(request -> assertThat(request.getURI().getRawQuery()).contains(
				"serviceKey=ab%2Bcd%2Fef%3D%3D",
				"type=json",
				"pageNo=1",
				"numOfRows=100",
				"DB_CLASS_NM=%ED%92%88%EB%AA%A9%EB%8C%80%ED%91%9C",
				"FOOD_NM_KR=%EB%91%90%EB%B6%80"))
			.andRespond(withSuccess(success("[]"), MediaType.APPLICATION_JSON));

		client.searchByName("두부");

		server.verify();
	}

	@Test
	void 검색_결과를_100g당_영양_값으로_바꾼다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(success("""
			[
			  {"FOOD_CD": "R101-1", "FOOD_NM_KR": "두부", "DB_GRP_NM": "원재료성", "SERVING_SIZE": "100g",
			   "AMT_NUM1": "84.000", "AMT_NUM3": "9.300", "AMT_NUM4": "4.600", "AMT_NUM6": "1.800"},
			  {"FOOD_CD": "D201-2", "FOOD_NM_KR": "두부전골", "DB_GRP_NM": "음식", "SERVING_SIZE": "100g",
			   "AMT_NUM1": "1,010.000", "AMT_NUM3": "", "AMT_NUM4": "-", "AMT_NUM6": null}
			]
			"""), MediaType.APPLICATION_JSON));

		List<Food> foods = client.searchByName("두부");

		assertThat(foods).hasSize(2);
		Food tofu = foods.get(0);
		assertThat(tofu.foodCode()).isEqualTo("R101-1");
		assertThat(tofu.foodName()).isEqualTo("두부");
		assertThat(tofu.groupName()).isEqualTo("원재료성");
		assertThat(tofu.baseAmountG()).isEqualByComparingTo("100");
		assertThat(tofu.caloriesKcal()).isEqualByComparingTo("84");
		assertThat(tofu.carbohydrateG()).isEqualByComparingTo("1.8");
		assertThat(tofu.proteinG()).isEqualByComparingTo("9.3");
		assertThat(tofu.fatG()).isEqualByComparingTo("4.6");

		Food stew = foods.get(1);
		assertThat(stew.caloriesKcal()).isEqualByComparingTo("1010");
		assertThat(stew.carbohydrateG()).isNull();
		assertThat(stew.proteinG()).isNull();
		assertThat(stew.fatG()).isNull();
	}

	@Test
	void 기준량이_mL이거나_없으면_숫자만_읽거나_100으로_둔다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(success("""
			[
			  {"FOOD_CD": "R1", "FOOD_NM_KR": "우유", "SERVING_SIZE": "100mL", "AMT_NUM1": "65"},
			  {"FOOD_CD": "R2", "FOOD_NM_KR": "두유", "AMT_NUM1": "60"}
			]
			"""), MediaType.APPLICATION_JSON));

		List<Food> foods = client.searchByName("우유");

		assertThat(foods).extracting(Food::baseAmountG)
			.usingElementComparator(BigDecimal::compareTo)
			.containsExactly(BigDecimal.valueOf(100), BigDecimal.valueOf(100));
	}

	@Test
	void 결과가_하나라_객체로_오거나_item_안에_있어도_읽는다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(success("""
			{"FOOD_CD": "R1", "FOOD_NM_KR": "두부", "AMT_NUM1": "84"}
			"""), MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(success("""
			{"item": [{"FOOD_CD": "R1", "FOOD_NM_KR": "두부", "AMT_NUM1": "84"}]}
			"""), MediaType.APPLICATION_JSON));

		assertThat(client.searchByName("두부")).extracting(Food::foodName).containsExactly("두부");
		assertThat(client.searchByName("두부")).extracting(Food::foodName).containsExactly("두부");
	}

	@Test
	void 식품코드나_이름이_없는_항목은_뺀다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(success("""
			[
			  {"FOOD_CD": "", "FOOD_NM_KR": "두부", "AMT_NUM1": "84"},
			  {"FOOD_CD": "R2", "FOOD_NM_KR": " ", "AMT_NUM1": "84"},
			  {"FOOD_CD": "R3", "FOOD_NM_KR": "두부", "AMT_NUM1": "84"}
			]
			"""), MediaType.APPLICATION_JSON));

		assertThat(client.searchByName("두부")).extracting(Food::foodCode).containsExactly("R3");
	}

	@Test
	void 결과가_없으면_빈_목록을_준다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess("""
			{"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
			 "body": {"pageNo": 1, "totalCount": 0, "numOfRows": 100, "items": ""}}
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess("""
			{"header": {"resultCode": "03", "resultMsg": "NODATA_ERROR"}}
			""", MediaType.APPLICATION_JSON));

		assertThat(client.searchByName("없는식품")).isEmpty();
		assertThat(client.searchByName("없는식품")).isEmpty();
	}

	@Test
	void 실패_결과_코드는_영양_정보_조회_실패로_처리한다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess("""
			{"header": {"resultCode": "22", "resultMsg": "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}
			""", MediaType.APPLICATION_JSON));

		assertFetchFailed();
	}

	@Test
	void 등록되지_않은_키는_영양_정보_조회_실패로_처리한다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withStatus(HttpStatus.FORBIDDEN)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
				{"OpenAPI_ServiceResponse": {"cmmMsgHeader": {"errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
				 "returnAuthMsg": "등록되지 않은 서비스키", "returnReasonCode": "30"}}}
				"""));

		assertFetchFailed();
	}

	@Test
	void 서버_오류는_영양_정보_조회_실패로_처리한다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withServerError());

		assertFetchFailed();
	}

	@Test
	void JSON이_아닌_응답은_영양_정보_조회_실패로_처리한다() {
		server.expect(requestTo(startsWith(ENDPOINT))).andRespond(withSuccess(
			"<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg></cmmMsgHeader></OpenAPI_ServiceResponse>",
			MediaType.APPLICATION_XML));

		assertFetchFailed();
	}

	@Test
	void 서비스_키가_없으면_부르지_않고_실패한다() {
		FoodNutritionClient clientWithoutKey = new FoodNutritionClient(RestClient.create(BASE_URL), properties(""));

		assertThatThrownBy(() -> clientWithoutKey.searchByName("두부"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NUTRITION_FETCH_FAILED);
	}

	private void assertFetchFailed() {
		assertThatThrownBy(() -> client.searchByName("두부"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NUTRITION_FETCH_FAILED);
	}

	private static String success(String items) {
		return """
			{"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
			 "body": {"pageNo": 1, "totalCount": 1, "numOfRows": 100, "items": %s}}
			""".formatted(items);
	}

	private static FoodNutritionProperties properties(String serviceKey) {
		return new FoodNutritionProperties(serviceKey, BASE_URL, 100, Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

}
