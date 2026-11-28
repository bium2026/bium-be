package com.bium.domain.user.dto;

import com.bium.domain.auth.dto.SignupRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 새 비밀번호는 가입(SignupRequest)과 같은 규칙으로 본다. 지금 비밀번호는 예전 규칙으로 만들었을 수 있어 비었는지만 본다
public record PasswordChangeRequest(

	@NotBlank(message = "지금 비밀번호를 입력해 주세요.")
	String currentPassword,

	@NotBlank(message = "새 비밀번호를 입력해 주세요.")
	@Pattern(regexp = SignupRequest.PASSWORD_PATTERN, message = SignupRequest.PASSWORD_PATTERN_MESSAGE)
	String newPassword

) {
}
