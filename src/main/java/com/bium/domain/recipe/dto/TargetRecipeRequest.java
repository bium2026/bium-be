package com.bium.domain.recipe.dto;

import com.bium.global.util.SpaceTrimmer;
import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 음식 이름은 Gemini 프롬프트에 들어가서 줄바꿈과 제어 문자를 막는다
public record TargetRecipeRequest(
	@NotBlank(message = "만들고 싶은 음식을 입력해 주세요.")
	@Size(max = 50, message = "음식 이름은 50자까지 입력할 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "음식 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String dishName
) {

	// 전각 공백처럼 @NotBlank가 공백으로 보지 않는 문자만 있는 이름도 막으려고 검사 전에 앞뒤 공백을 뺀다.
	// 줄바꿈·탭은 빼지 않고 남겨 두어 @Pattern이 막게 한다
	public TargetRecipeRequest {
		dishName = SpaceTrimmer.trim(dishName);
	}

}
