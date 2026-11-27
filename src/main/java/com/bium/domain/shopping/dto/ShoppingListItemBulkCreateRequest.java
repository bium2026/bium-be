package com.bium.domain.shopping.dto;

import java.util.List;

import com.bium.global.util.SpaceTrimmer;
import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 다음 구매 날짜 달력에서 고른 날의 재료처럼 여러 이름을 한 번에 담는다. 이름마다 담기와 같은 규칙을 쓴다
public record ShoppingListItemBulkCreateRequest(
	@NotEmpty(message = "장보기 목록에 담을 재료를 골라 주세요.")
	@Size(max = 30, message = "재료는 한 번에 30개까지 담을 수 있습니다.")
	List<
		@NotBlank(message = "재료 이름을 입력해 주세요.")
		@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
		@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
		String> names
) {

	// 길이는 저장할 값으로 재야 하므로 검사 전에 앞뒤 공백을 뺀다
	public ShoppingListItemBulkCreateRequest {
		names = names == null ? null : names.stream().map(SpaceTrimmer::trim).toList();
	}

}
