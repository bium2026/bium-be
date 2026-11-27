package com.bium.domain.user.dto;

import com.bium.domain.auth.dto.SignupRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 가입(SignupRequest)과 같이 앞뒤 공백을 빼고 2~10자인지 본다
public record NicknameChangeRequest(

	@NotBlank(message = SignupRequest.NICKNAME_REQUIRED_MESSAGE)
	@Size(min = 2, max = 10, message = SignupRequest.NICKNAME_SIZE_MESSAGE)
	String nickname

) {

	public NicknameChangeRequest {
		nickname = SignupRequest.normalizeNickname(nickname);
	}

}
