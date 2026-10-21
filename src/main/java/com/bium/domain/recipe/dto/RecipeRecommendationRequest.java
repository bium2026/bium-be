package com.bium.domain.recipe.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import com.bium.domain.recipe.recommendation.RecommendationIngredient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 냉장고 엔티티가 생기기 전까지 추천에 쓸 재료를 요청 본문으로 받는다.
 * 재료 이름은 프롬프트에 그대로 들어가므로 길이와 줄바꿈·제어 문자를 막는다.
 */
public record RecipeRecommendationRequest(
	@NotEmpty(message = "재료를 하나 이상 보내 주세요.")
	@Size(max = 100, message = "재료는 100개까지 보낼 수 있습니다.")
	List<@NotNull(message = "재료를 보내 주세요.") @Valid Ingredient> ingredients,
	Boolean prioritizeUrgent
) {

	// \p{Cntrl}은 ASCII만 보므로 U+2028 같은 유니코드 줄바꿈과 방향 제어 문자까지 막는다
	private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
	// 이모지는 여러 글자를 잇는 ZWJ(U+200D)가 형식 문자라서 형식 문자는 허용한다
	private static final String NO_EMOJI_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Zl}\\p{Zp}]*";

	public record Ingredient(
		@NotBlank(message = "재료 이름을 입력해 주세요.")
		@Size(max = 30, message = "재료 이름은 30자까지 쓸 수 있습니다.")
		@Pattern(regexp = NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
		String name,
		@Size(max = 16, message = "아이콘이 너무 깁니다.")
		@Pattern(regexp = NO_EMOJI_CONTROL_CHARACTER, message = "아이콘에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
		String emoji,
		LocalDate expiresAt
	) {
	}

	// 저장된 재료가 아니라 id가 없으므로 요청 안의 순서를 id로 쓴다
	public List<RecommendationIngredient> toIngredients() {
		return IntStream.range(0, ingredients.size())
			.mapToObj(index -> {
				Ingredient ingredient = ingredients.get(index);
				return new RecommendationIngredient((long)index, ingredient.name().strip(), ingredient.emoji(),
					ingredient.expiresAt());
			})
			.toList();
	}

	public boolean isPrioritizeUrgent() {
		return Boolean.TRUE.equals(prioritizeUrgent);
	}

}
