package com.bium.domain.user.dto;

import com.bium.domain.user.entity.User;

// email은 아이디로 로그인하던 때 만든 계정이면 비어 있을 수 있다
public record MyProfileResponse(String email, String nickname) {

	public static MyProfileResponse from(User user) {
		return new MyProfileResponse(user.getEmail(), user.getNickname());
	}

}
