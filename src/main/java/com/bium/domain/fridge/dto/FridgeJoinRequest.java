package com.bium.domain.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FridgeJoinRequest(
	@NotBlank(message = "초대 코드를 입력해 주세요.")
	@Size(max = 12, message = "초대 코드가 너무 깁니다.")
	String code
) {
}
