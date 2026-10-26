package com.bium.domain.fridge.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.fridge.dto.FridgeInviteResponse;
import com.bium.domain.fridge.dto.FridgeJoinRequest;
import com.bium.domain.fridge.dto.FridgeResponse;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeInvite;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeInviteRepository;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.user.repository.UserRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 소유자가 만든 초대 코드로 다른 사용자를 냉장고 멤버로 들인다.
 * 쓸 수 있는 코드는 냉장고마다 하나이며, 새로 만들면 전에 만든 코드는 바로 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FridgeInviteService {

	// 사람이 보고 옮겨 적어도 틀리지 않게 헷갈리는 0·O·1·I는 뺀다
	private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 8;
	private static final int MAX_CODE_ATTEMPTS = 5;
	private static final Duration INVITE_VALIDITY = Duration.ofHours(24);

	private final FridgeInviteRepository fridgeInviteRepository;
	private final FridgeMemberRepository fridgeMemberRepository;
	private final FridgeRepository fridgeRepository;
	private final UserRepository userRepository;
	private final FridgeAccessValidator fridgeAccessValidator;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public FridgeInviteResponse create(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateOwner(fridgeId, userId);
		Fridge fridge = fridgeRepository.findByIdForUpdate(fridgeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
		fridgeInviteRepository.findAllActiveByFridgeId(fridgeId).forEach(FridgeInvite::deactivate);

		// 화면에 보여 줄 시각이라 초 아래는 버린다
		LocalDateTime expiresAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).plus(INVITE_VALIDITY);
		FridgeInvite invite = fridgeInviteRepository.save(new FridgeInvite(fridge, generateCode(),
			userRepository.getReferenceById(userId), expiresAt));
		return FridgeInviteResponse.from(invite);
	}

	// 쓸 수 있는 코드가 없으면 null을 돌려준다
	@Transactional(readOnly = true)
	public FridgeInviteResponse getActive(Long userId, Long fridgeId) {
		fridgeAccessValidator.validateOwner(fridgeId, userId);
		LocalDateTime now = LocalDateTime.now(clock);
		return fridgeInviteRepository.findAllActiveByFridgeId(fridgeId).stream()
			.filter(invite -> invite.isUsable(now))
			.max(Comparator.comparing(FridgeInvite::getId))
			.map(FridgeInviteResponse::from)
			.orElse(null);
	}

	public FridgeResponse join(Long userId, FridgeJoinRequest request) {
		// 소문자나 앞뒤 공백을 섞어 입력해도 같은 코드로 본다
		String code = request.code().strip().toUpperCase(Locale.ROOT);
		FridgeInvite invite = fridgeInviteRepository.findByCode(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
		// 새 코드로 바뀐 코드도 더는 쓸 수 없으므로 만료와 같은 오류를 준다
		if (!invite.isUsable(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.INVITE_EXPIRED);
		}
		if (fridgeMemberRepository.existsByFridgeIdAndUserId(invite.getFridge().getId(), userId)) {
			throw new BusinessException(ErrorCode.ALREADY_FRIDGE_MEMBER);
		}

		FridgeMember member = new FridgeMember(invite.getFridge(), userRepository.getReferenceById(userId),
			MemberRole.MEMBER);
		try {
			fridgeMemberRepository.saveAndFlush(member);
		} catch (DataIntegrityViolationException e) {
			// 참여 버튼을 두 번 눌러 동시에 들어온 요청은 (냉장고, 사용자) unique 제약에 걸린다
			throw new BusinessException(ErrorCode.ALREADY_FRIDGE_MEMBER, e);
		}
		return FridgeResponse.from(member);
	}

	// 전에 만든 코드와 겹치면 다시 뽑는다
	private String generateCode() {
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String code = randomCode();
			if (!fridgeInviteRepository.existsByCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("겹치지 않는 초대 코드를 만들지 못했습니다.");
	}

	private String randomCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
		}
		return code.toString();
	}

}
