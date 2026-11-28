package com.bium.domain.ingredient.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 재료 수정 모달에서 고친 값을 받는다. 보낸 값으로 모든 칸을 바꾸며, expiresAt이 null이면 유통기한을 지운다.
 * quantity가 null이면 수량을 세지 않는 재료("있음")로 두고 unit은 쓰지 않는다. unit을 비우면 "개"로 본다.
 * version은 화면이 재료를 불러올 때 받은 값이며, 그 사이 다른 멤버가 고쳤으면 저장하지 않는다.
 * 재료 이름은 레시피 추천 프롬프트에 들어가므로 줄바꿈·제어 문자를 막는다.
 */
public record FridgeItemUpdateRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name,
	@Size(max = 16, message = "아이콘이 너무 깁니다.")
	@Pattern(regexp = TextPatterns.SINGLE_EMOJI, message = "아이콘은 이모지 한 개로 넣어 주세요.")
	String emoji,
	LocalDate expiresAt,
	@DecimalMin(value = "0", inclusive = false, message = "수량은 0보다 커야 합니다.")
	@Digits(integer = 6, fraction = 2, message = "수량은 999999.99까지, 소수점 둘째 자리까지 쓸 수 있습니다.")
	BigDecimal quantity,
	@Size(max = 10, message = "단위는 10자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "단위에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String unit,
	@NotNull(message = "재료 버전을 보내 주세요.")
	Integer version
) {
}
