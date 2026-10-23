package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.StorageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 도감 재료를 냉장고에 넣을 때 받는다. 화면은 도감의 이름·아이콘을 채워 보내고, 사용자가 고칠 수 있다.
 * 이름·아이콘 규칙은 재료 수정(FridgeItemUpdateRequest)과 같다. emoji를 비워 보내면 아이콘 없이 넣는다.
 */
public record FridgeItemCreateRequest(
	@NotNull(message = "도감 재료를 선택해 주세요.")
	Long catalogId,
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name,
	@Size(max = 16, message = "아이콘이 너무 깁니다.")
	@Pattern(regexp = NO_EMOJI_CONTROL_CHARACTER, message = "아이콘에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String emoji,
	@NotNull(message = "보관 위치를 선택해 주세요.")
	StorageType storage
) {

	// FridgeItemUpdateRequest와 같은 규칙이다
	private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	private static final String NO_EMOJI_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Zl}\\p{Zp}]*";

}
