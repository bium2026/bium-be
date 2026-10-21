package com.bium.global.gemini;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
	String apiKey,
	String model,
	String baseUrl,
	double temperature,
	Duration connectTimeout,
	Duration readTimeout
) {
}
