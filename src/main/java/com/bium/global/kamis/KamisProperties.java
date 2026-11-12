package com.bium.global.kamis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kamis")
public record KamisProperties(
	String certKey,
	String certId,
	String baseUrl,
	Duration connectTimeout,
	Duration readTimeout
) {
}
