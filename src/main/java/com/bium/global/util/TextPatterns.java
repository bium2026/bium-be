package com.bium.global.util;

/**
 * 요청 값을 @Pattern으로 검사할 때 쓰는 정규식이다. 재료·음식 이름은 Gemini 프롬프트에 들어가서 줄바꿈·제어 문자를 막는다.
 */
public final class TextPatterns {

	// \p{Cntrl}은 ASCII만 보므로 U+2028 같은 유니코드 줄바꿈과 방향 제어 문자까지 막는다
	public static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	// 이모지는 여러 글자를 잇는 ZWJ(U+200D)가 형식 문자라서 형식 문자는 허용한다
	public static final String NO_EMOJI_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Zl}\\p{Zp}]*";

	private TextPatterns() {
	}

}
