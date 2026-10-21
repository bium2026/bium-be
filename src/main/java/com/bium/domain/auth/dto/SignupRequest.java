package com.bium.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

	@NotBlank(message = "아이디를 입력해 주세요.")
	@Pattern(regexp = "^[a-z0-9]{4,20}$", message = "아이디는 4~20자의 영문 소문자와 숫자만 사용할 수 있습니다.")
	String loginId,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,20}$", message = "비밀번호는 공백 없이 영문과 숫자를 포함한 8~20자여야 합니다.")
	String password,

	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
	String nickname

) {
}
