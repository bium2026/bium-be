package com.bium.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 토큰 갱신(POST /api/auth/refresh)과 로그아웃(POST /api/auth/logout)에 로그인 때 받은 리프레시 토큰을 보낸다
public record RefreshTokenRequest(

	@NotBlank(message = "리프레시 토큰을 보내 주세요.")
	String refreshToken

) {
}
