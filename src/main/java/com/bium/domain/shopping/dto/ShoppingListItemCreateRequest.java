package com.bium.domain.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingListItemCreateRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	String name
) {

	// 길이는 저장할 값으로 재야 하므로 검사 전에 앞뒤 공백을 뺀다
	public ShoppingListItemCreateRequest {
		name = name == null ? null : name.strip();
	}

}
