package com.bium.domain.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.dto.LoginRequest;
import com.bium.domain.auth.dto.LoginResponse;
import com.bium.domain.auth.dto.RefreshTokenRequest;
import com.bium.domain.auth.dto.SignupRequest;
import com.bium.domain.auth.dto.SignupResponse;
import com.bium.domain.auth.service.RefreshTokenService.RotatedRefreshToken;
import com.bium.domain.fridge.service.FridgeService;
import com.bium.domain.notification.service.NotificationSettingService;
import com.bium.domain.user.entity.User;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final RefreshTokenService refreshTokenService;
	private final FridgeService fridgeService;
	private final NotificationSettingService notificationSettingService;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}

		User user = new User(request.email(), passwordEncoder.encode(request.password()), request.nickname());
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 동시에 가입하면 위 확인을 둘 다 통과하고 email 유니크 제약에서 걸린다
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		// 같은 트랜잭션에서 만들어, 냉장고를 만들다 실패하면 가입도 취소되고 냉장고 없는 계정이 남지 않는다
		fridgeService.createDefaultFridge(user);
		notificationSettingService.createDefault(user);
		return new SignupResponse(user.getId());
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		// 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않아 가입된 이메일을 알아낼 수 없게 한다
		User user = userRepository.findAllByEmail(request.email()).stream()
			.filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

		return issueTokens(user);
	}

	// 같은 기기에서 다시 로그인하지 않고 액세스 토큰을 새로 받는다. 받은 리프레시 토큰도 새것으로 바꿔 준다
	@Transactional
	public LoginResponse refresh(RefreshTokenRequest request) {
		RotatedRefreshToken rotated = refreshTokenService.rotate(request.refreshToken());
		return new LoginResponse(jwtProvider.createAccessToken(rotated.userId()),
			jwtProvider.getAccessTokenValiditySeconds(), rotated.refreshToken());
	}

	// 이 기기의 리프레시 토큰만 지운다. 이미 쓸 수 없는 토큰이어도 로그아웃은 성공으로 본다
	@Transactional
	public void logout(RefreshTokenRequest request) {
		refreshTokenService.revoke(request.refreshToken());
	}

	// 비밀번호를 바꾼 뒤처럼 지금 기기에 새 토큰을 줄 때도 쓴다
	@Transactional
	public LoginResponse issueTokens(User user) {
		return new LoginResponse(jwtProvider.createAccessToken(user.getId()), jwtProvider.getAccessTokenValiditySeconds(),
			refreshTokenService.issue(user));
	}

}
