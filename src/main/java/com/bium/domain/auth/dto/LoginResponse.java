package com.bium.domain.auth.dto;

// expiresIn: 액세스 토큰 유효 시간(초). refreshToken: 액세스 토큰이 만료되면 POST /api/auth/refresh로 새 토큰을 받는 데 쓴다
public record LoginResponse(String accessToken, long expiresIn, String refreshToken) {
}
