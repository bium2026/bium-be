package com.bium.global.util;

/**
 * 요청 값의 앞뒤 공백(전각 공백 포함)을 뺀다. 줄바꿈·탭 같은 제어 문자는 남겨서 검증에서 막게 한다.
 */
public final class SpaceTrimmer {

	private SpaceTrimmer() {
	}

	// 정규식으로 끝 공백을 지우면 긴 공백에서 오래 걸릴 수 있어 양 끝에서 한 글자씩 본다
	public static String trim(String value) {
		if (value == null) {
			return null;
		}
		int start = 0;
		int end = value.length();
		while (start < end && isSpace(value.charAt(start))) {
			start++;
		}
		while (end > start && isSpace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	private static boolean isSpace(char character) {
		return Character.getType(character) == Character.SPACE_SEPARATOR;
	}

}
