package com.bium.global.nutrition;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 공공데이터포털의 식약처 식품영양성분 DB API 설정. 서비스 키는 서버 환경변수로만 받는다
@ConfigurationProperties(prefix = "nutrition-api")
public record FoodNutritionProperties(
	String serviceKey,
	String baseUrl,
	int pageSize,
	Duration connectTimeout,
	Duration readTimeout
) {
}
