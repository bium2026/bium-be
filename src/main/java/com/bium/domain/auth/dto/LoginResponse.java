package com.bium.domain.auth.dto;

// expiresIn: 토큰 유효 시간(초)
public record LoginResponse(String accessToken, long expiresIn) {
}
