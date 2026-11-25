package com.bium.domain.auth.dto;

import java.util.Locale;

import com.bium.global.util.SpaceTrimmer;

import jakarta.validation.constraints.NotBlank;

// 가입할 때처럼 앞뒤 공백을 빼고 소문자로 바꿔 찾는다
public record LoginRequest(

	@NotBlank(message = "이메일을 입력해 주세요.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	String password

) {

	public LoginRequest {
		email = email == null ? null : SpaceTrimmer.trim(email).toLowerCase(Locale.ROOT);
	}

}
