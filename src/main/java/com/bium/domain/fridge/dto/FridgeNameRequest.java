package com.bium.domain.fridge.dto;

import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 새 냉장고를 만들거나 이름을 바꿀 때 받는다. 길이는 fridges.name 칸(50자)에 맞춘다.
// 이름은 헤더·피드에 그대로 보이므로 줄바꿈·제어 문자를 막는다
public record FridgeNameRequest(
	@NotBlank(message = "냉장고 이름을 입력해 주세요.")
	@Size(max = 50, message = "냉장고 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "냉장고 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name
) {
}
