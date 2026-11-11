package com.bium.global.nutrition;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties(FoodNutritionProperties.class)
public class FoodNutritionConfig {

	@Bean
	public FoodNutritionClient foodNutritionClient(FoodNutritionProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());

		RestClient restClient = RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.build();
		FoodNutritionClient client = new FoodNutritionClient(restClient, properties);
		// 요청마다 남기면 로그가 쌓여서 서버를 켤 때 한 번만 남긴다
		if (!client.isConfigured()) {
			log.error("NUTRITION_API_KEY 환경변수가 설정되지 않았습니다. 키를 넣을 때까지 영양 정보 조회는 502로 실패합니다.");
		}
		return client;
	}

}
