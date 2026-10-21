package com.bium.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.bium.global.exception.RestAuthenticationEntryPoint;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String AUTH_API = "/api/auth/**";
	private static final String RECIPE_RECOMMENDATION_API = "/api/recipes/recommendations";

	private final RestAuthenticationEntryPoint authenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// CorsConfig의 CorsConfigurationSource를 사용한다
			.cors(Customizer.withDefaults())
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// 오류 응답을 만드는 내부 이동까지 막으면 공개 API의 오류도 401이 된다
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(AUTH_API).permitAll()
				// 로그인을 붙이기 전까지 공개하고 호출 수로 제한한다
				.requestMatchers(HttpMethod.POST, RECIPE_RECOMMENDATION_API).permitAll()
				.anyRequest().authenticated())
			// Authorization: Bearer 헤더의 토큰을 JwtConfig의 JwtDecoder로 검증한다
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(Customizer.withDefaults())
				.bearerTokenResolver(bearerTokenResolver())
				.authenticationEntryPoint(authenticationEntryPoint))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 만료된 토큰이 붙은 채로 공개 API를 요청해도 401로 막히지 않도록 공개 API에서는 토큰을 읽지 않는다
	private BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
		RequestMatcher publicApi = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(AUTH_API),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, RECIPE_RECOMMENDATION_API));
		return request -> publicApi.matches(request) ? null : defaultResolver.resolve(request);
	}

}
