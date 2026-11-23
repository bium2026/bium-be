package com.bium.domain.feed.dto;

import com.bium.global.util.SpaceTrimmer;
import com.bium.global.util.TextPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 추천한 음식 이름은 레시피 추천(Gemini)에 그대로 넘길 수 있어서 레시피 추천 요청과 같은 조건으로 막는다
public record FeedCommentCreateRequest(
	@NotBlank(message = "코멘트를 입력해 주세요.")
	@Size(max = 200, message = "코멘트는 200자까지 쓸 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_EMOJI_CONTROL_CHARACTER, message = "코멘트에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String content,

	@Size(max = 50, message = "음식 이름은 50자까지 입력할 수 있습니다.")
	@Pattern(regexp = TextPatterns.NO_CONTROL_CHARACTER, message = "음식 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
	String dishName
) {

	// 길이는 저장할 값으로 재야 하므로 검사 전에 앞뒤 공백을 뺀다. 줄바꿈·탭은 남겨 두어 @Pattern이 막게 한다.
	// 음식 이름은 고르지 않아도 되니 공백뿐이면 추천하지 않은 것으로 본다
	public FeedCommentCreateRequest {
		content = SpaceTrimmer.trim(content);
		dishName = SpaceTrimmer.trim(dishName);
		if (dishName != null && dishName.isEmpty()) {
			dishName = null;
		}
	}

}
