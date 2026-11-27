package com.bium.domain.shopping.dto;

import com.bium.global.util.SpaceTrimmer;
import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 담을 때와 같은 규칙으로 이름을 받는다
public record ShoppingListItemRenameRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name
) {

	// 길이는 저장할 값으로 재야 하므로 검사 전에 앞뒤 공백을 뺀다.
	// 줄바꿈·탭은 빼지 않고 남겨 두어 @Pattern이 막게 한다
	public ShoppingListItemRenameRequest {
		name = SpaceTrimmer.trim(name);
	}

}
