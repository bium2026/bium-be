package com.bium.domain.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 음식 이름은 Gemini 프롬프트에 들어가서 줄바꿈과 제어 문자를 막는다
public record TargetRecipeRequest(
	@NotBlank(message = "만들고 싶은 음식을 입력해 주세요.")
	@Size(max = 50, message = "음식 이름은 50자까지 입력할 수 있습니다.")
	@Pattern(regexp = NO_CONTROL_CHARACTER, message = "음식 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String dishName
) {

	// 냉장고 재료 이름(FridgeItemCreateRequest)과 같은 규칙이다
	private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	private static final String SURROUNDING_SPACE = "^\\p{Zs}+|\\p{Zs}+$";

	// 전각 공백처럼 @NotBlank가 공백으로 보지 않는 문자만 있는 이름도 막으려고 검사 전에 앞뒤 공백을 뺀다.
	// 줄바꿈·탭은 빼지 않고 남겨 두어 @Pattern이 막게 한다
	public TargetRecipeRequest {
		dishName = dishName == null ? null : dishName.replaceAll(SURROUNDING_SPACE, "");
	}

}
