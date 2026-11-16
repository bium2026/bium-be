package com.bium.domain.ingredient.dto;

import java.time.LocalDate;

import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 재료 수정 모달에서 고친 값을 받는다. 보낸 값으로 세 칸을 모두 바꾸며, expiresAt이 null이면 유통기한을 지운다.
 * version은 화면이 재료를 불러올 때 받은 값이며, 그 사이 다른 멤버가 고쳤으면 저장하지 않는다.
 * 재료 이름은 레시피 추천 프롬프트에 들어가므로 줄바꿈·제어 문자를 막는다.
 */
public record FridgeItemUpdateRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name,
	@Size(max = 16, message = "아이콘이 너무 깁니다.")
	@Pattern(regexp = TextPatterns.NO_EMOJI_CONTROL_CHARACTER, message = "아이콘에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String emoji,
	LocalDate expiresAt,
	@NotNull(message = "재료 버전을 보내 주세요.")
	Integer version
) {
}
