package com.bium.domain.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

	private final JwtEncoder jwtEncoder;
	private final Duration accessTokenValidity;

	public JwtProvider(JwtEncoder jwtEncoder, @Value("${jwt.access-token-validity}") Duration accessTokenValidity) {
		this.jwtEncoder = jwtEncoder;
		this.accessTokenValidity = accessTokenValidity;
	}

	// 토큰의 sub에 사용자 id를 담는다. 인증된 요청에서는 @AuthenticationPrincipal Jwt의 getSubject()로 꺼낸다
	public String createAccessToken(Long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(userId))
			.issuedAt(now)
			.expiresAt(now.plus(accessTokenValidity))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long getAccessTokenValiditySeconds() {
		return accessTokenValidity.toSeconds();
	}

}
