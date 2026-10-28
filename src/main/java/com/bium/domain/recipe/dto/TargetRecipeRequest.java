package com.bium.domain.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TargetRecipeRequest(
	@NotBlank(message = "만들고 싶은 음식을 입력해 주세요.")
	@Size(max = 50, message = "음식 이름은 50자까지 입력할 수 있습니다.")
	String dishName
) {
}
