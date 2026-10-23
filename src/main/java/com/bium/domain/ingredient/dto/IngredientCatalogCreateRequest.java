package com.bium.domain.ingredient.dto;

import com.bium.domain.ingredient.entity.IngredientCategory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 도감의 새 재료 추가 모달에서 입력한 값을 받는다. 길이와 범위는 모달의 검사 규칙과 같다.
 * 재료 이름은 냉장고 재료로 옮겨져 레시피 추천 프롬프트에 들어가므로 줄바꿈·제어 문자를 막는다.
 */
public record IngredientCatalogCreateRequest(
	@NotBlank(message = "재료 이름을 입력해 주세요.")
	@Size(max = 30, message = "재료 이름은 30자까지 쓸 수 있습니다.")
	@Pattern(regexp = NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String name,
	@NotBlank(message = "아이콘을 입력해 주세요.")
	@Size(max = 16, message = "아이콘이 너무 깁니다.")
	@Pattern(regexp = NO_EMOJI_CONTROL_CHARACTER, message = "아이콘에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String emoji,
	@NotNull(message = "카테고리를 선택해 주세요.")
	IngredientCategory category,
	@NotNull(message = "기본 유통기한을 입력해 주세요.")
	@Min(value = 1, message = "기본 유통기한은 1~365일 사이로 입력해 주세요.")
	@Max(value = 365, message = "기본 유통기한은 1~365일 사이로 입력해 주세요.")
	Integer shelfLifeDays
) {

	// FridgeItemUpdateRequest와 같은 규칙이다
	private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	private static final String NO_EMOJI_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Zl}\\p{Zp}]*";

}
