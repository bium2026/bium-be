package com.bium.domain.recipe.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.bium.domain.ingredient.dto.FridgeItemResponse;

/**
 * 요리 완료 전에 레시피 재료마다 뺄 냉장고 재료 후보를 보여 준다.
 * candidates는 유통기한이 지나지 않은 재료부터 유통기한이 빠른 순이고, 지난 재료는 맨 뒤에 있다. 맞는 재료가 없으면 빈 배열이다.
 * 레시피 추천은 유통기한이 지난 재료를 없는 재료로 보므로, 화면은 expired인 재료를 미리 고르지 않는다.
 */
public record CookingCandidatesResponse(Long recipeId, List<IngredientCandidates> ingredients) {

	public record IngredientCandidates(String name, String amount, List<Candidate> candidates) {
	}

	// 냉장고 재료 응답(FridgeItemResponse)과 같은 필드에 유통기한이 지났는지(expired)를 더한다
	// 화면은 quantity가 있는 재료만 쓴 양을 고르게 한다
	public record Candidate(Long id, String name, String emoji, String imageUrl, LocalDate expiresAt,
		LocalDate purchasedAt, BigDecimal quantity, String unit, Integer version, boolean expired) {

		public static Candidate of(FridgeItemResponse item, boolean expired) {
			return new Candidate(item.id(), item.name(), item.emoji(), item.imageUrl(), item.expiresAt(),
				item.purchasedAt(), item.quantity(), item.unit(), item.version(), expired);
		}
	}

}
