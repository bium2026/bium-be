package com.bium.domain.shopping.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// names는 레시피 재료 중 기본 양념(BASIC)이 아닌 재료의 이름이다. 화면은 지금 냉장고에 없는 재료만 보낸다
public record MissingIngredientAddRequest(
	@NotEmpty(message = "장보기 목록에 담을 재료를 골라 주세요.")
	@Size(max = 30, message = "재료는 한 번에 30개까지 담을 수 있습니다.")
	List<@NotBlank(message = "재료 이름이 비어 있습니다.") String> names
) {
}
