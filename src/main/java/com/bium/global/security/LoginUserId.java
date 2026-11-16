package com.bium.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 인증된 요청의 사용자 id를 컨트롤러 파라미터로 받는다.
 * 토큰의 sub에 담긴 사용자 id(JwtProvider)를 Long으로 바꿔 준다. @AuthenticationPrincipal을 메타 어노테이션으로 써서 따로 등록하지 않아도 된다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface LoginUserId {
}
