package com.bium.domain.ingredient.dto;

import java.time.LocalDate;
import java.util.List;

import com.bium.domain.ingredient.entity.StorageType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 영수증에서 읽은 재료처럼 도감에 없는 재료를 한 번에 넣을 때 받는다. 하나라도 잘못되면 아무것도 넣지 않는다.
 * 이름·아이콘 규칙은 재료 수정(FridgeItemUpdateRequest)과 같다.
 * emoji를 비워 보내면 아이콘 없이, expiresAt이 null이면 유통기한 없이 넣는다.
 */
public record FridgeItemBulkCreateRequest(
	// 영수증 인식 결과(최대 50개)를 그대로 넣을 수 있는 수
	@NotEmpty(message = "넣을 재료를 하나 이상 보내 주세요.")
	@Size(max = 50, message = "재료는 한 번에 50개까지 넣을 수 있습니다.")
	List<@NotNull(message = "재료 정보를 보내 주세요.") @Valid Item> items
) {

	public record Item(
		@NotBlank(message = "재료 이름을 입력해 주세요.")
		@Size(max = 50, message = "재료 이름은 50자까지 쓸 수 있습니다.")
		@Pattern(regexp = NO_CONTROL_CHARACTER, message = "재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
		String name,
		@Size(max = 16, message = "아이콘이 너무 깁니다.")
		@Pattern(regexp = NO_EMOJI_CONTROL_CHARACTER, message = "아이콘에 줄바꿈이나 제어 문자를 쓸 수 없습니다.")
		String emoji,
		@NotNull(message = "보관 위치를 선택해 주세요.")
		StorageType storage,
		LocalDate expiresAt
	) {

		// FridgeItemUpdateRequest와 같은 규칙이다
		private static final String NO_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*";
		private static final String NO_EMOJI_CONTROL_CHARACTER = "[^\\p{Cc}\\p{Zl}\\p{Zp}]*";

	}

}
