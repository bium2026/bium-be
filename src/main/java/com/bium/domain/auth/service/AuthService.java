package com.bium.domain.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.dto.LoginRequest;
import com.bium.domain.auth.dto.LoginResponse;
import com.bium.domain.auth.dto.SignupRequest;
import com.bium.domain.auth.dto.SignupResponse;
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

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByLoginId(request.loginId())) {
			throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
		}

		User user = new User(request.loginId(), passwordEncoder.encode(request.password()), request.nickname());
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			// 같은 아이디로 동시에 가입하면 위 확인을 둘 다 통과하고 login_id 유니크 제약에서 걸린다
			throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
		}
		return new SignupResponse(user.getId());
	}

	public LoginResponse login(LoginRequest request) {
		// 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않아 가입된 아이디를 알아낼 수 없게 한다
		User user = userRepository.findByLoginId(request.loginId())
			.filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
			.orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

		return new LoginResponse(jwtProvider.createAccessToken(user.getId()), jwtProvider.getAccessTokenValiditySeconds());
	}

}
