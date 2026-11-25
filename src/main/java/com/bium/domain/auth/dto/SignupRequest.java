package com.bium.domain.auth.dto;

import java.util.Locale;

import com.bium.global.util.SpaceTrimmer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * email은 로그인에 쓰고, 유통기한 알림을 받을 주소의 처음 값으로도 쓴다.
 * 대소문자만 다른 주소로 따로 가입하지 않게 소문자로 바꿔 저장한다.
 */
public record SignupRequest(

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Size(max = 255, message = "이메일이 너무 깁니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,20}$", message = "비밀번호는 공백 없이 영문과 숫자를 포함한 8~20자여야 합니다.")
	String password,

	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
	String nickname

) {

	public SignupRequest {
		email = email == null ? null : SpaceTrimmer.trim(email).toLowerCase(Locale.ROOT);
	}

}
