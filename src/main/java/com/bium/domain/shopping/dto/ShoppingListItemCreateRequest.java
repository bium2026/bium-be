package com.bium.domain.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShoppingListItemCreateRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name
) {

	// 냉장고 재료 이름(FridgeItemCreateRequest)과 같은 규칙이다
	private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	private static final String SURROUNDING_SPACE = "^\\p{Zs}+|\\p{Zs}+$";

	// 길이는 저장할 값으로 재야 하므로 검사 전에 앞뒤 공백을 뺀다.
	// 줄바꿈·탭은 빼지 않고 남겨 두어 @Pattern이 막게 한다
	public ShoppingListItemCreateRequest {
		name = name == null ? null : name.replaceAll(SURROUNDING_SPACE, "");
	}

}
