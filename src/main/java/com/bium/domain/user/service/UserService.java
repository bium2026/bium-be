package com.bium.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.user.dto.MyProfileResponse;
import com.bium.domain.user.dto.NicknameChangeRequest;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;

	public MyProfileResponse getMe(Long userId) {
		return MyProfileResponse.from(findUser(userId));
	}

	// 가입 때 닉네임으로 지은 냉장고 이름("강희의 냉장고")은 사용자가 정한 이름일 수 있어 그대로 둔다
	@Transactional
	public MyProfileResponse changeNickname(Long userId, NicknameChangeRequest request) {
		User user = findUser(userId);
		user.changeNickname(request.nickname());
		return MyProfileResponse.from(user);
	}

	// 토큰은 맞는데 계정이 없으면 다시 로그인해야 하므로 401로 알린다
	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

}
