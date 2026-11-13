package com.bium.domain.feed.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.feed.dto.FeedFridgeDetailResponse;
import com.bium.domain.feed.dto.FeedFridgeResponse;
import com.bium.domain.feed.dto.FeedItemPreview;
import com.bium.domain.feed.dto.FeedPageResponse;
import com.bium.domain.feed.dto.FeedStoragePreview;
import com.bium.domain.feed.dto.FeedStorages;
import com.bium.domain.feed.repository.FeedLikeRepository;
import com.bium.domain.feed.repository.FeedSaveRepository;
import com.bium.domain.feed.repository.FridgeIdCount;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.ingredient.dto.FridgeContentsResponse;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 소유자가 공개한 냉장고를 다른 사용자도 볼 수 있는 피드다.
 * 공개하지 않은 냉장고는 멤버라도 피드에서 찾을 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

	static final int PAGE_SIZE = 10;
	// 피드 카드에서 보관 구역마다 보여 줄 재료 수
	static final int PREVIEW_SIZE = 6;
	// 냉장고 화면에서 재료 카드를 임박으로 표시하는 기준과 같다
	static final int EXPIRING_DAYS = 3;

	// 선반 → 왼쪽 문칸 → 오른쪽 문칸, 칸 안에서는 놓인 순서
	private static final Comparator<FridgeItem> DISPLAY_ORDER = Comparator
		.comparing((FridgeItem item) -> item.getSection().ordinal())
		.thenComparing(FridgeItem::getSortOrder)
		.thenComparing(FridgeItem::getId);

	private final FridgeRepository fridgeRepository;
	private final FridgeMemberRepository fridgeMemberRepository;
	private final FridgeItemRepository fridgeItemRepository;
	private final FeedLikeRepository feedLikeRepository;
	private final FeedSaveRepository feedSaveRepository;
	private final Clock clock;

	// cursor가 없으면 처음부터, 있으면 그 냉장고 다음부터 준다. savedOnly면 내가 저장한 냉장고만 준다
	public FeedPageResponse getFeed(Long userId, Long cursor, boolean savedOnly) {
		long before = cursor == null ? Long.MAX_VALUE : cursor;
		// 한 개를 더 읽어 다음 페이지가 있는지 본다
		PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);
		List<Fridge> fridges = savedOnly
			? feedSaveRepository.findSavedPublicBefore(userId, before, page)
			: fridgeRepository.findPublicBefore(before, page);

		boolean hasNext = fridges.size() > PAGE_SIZE;
		List<Fridge> shown = hasNext ? fridges.subList(0, PAGE_SIZE) : fridges;
		List<Long> fridgeIds = shown.stream().map(Fridge::getId).toList();
		Map<Long, List<FridgeItem>> itemsByFridge = fridgeIds.isEmpty()
			? Map.of()
			: fridgeItemRepository.findAllByFridgeIdInOrderBySortOrder(fridgeIds).stream()
				.collect(Collectors.groupingBy(item -> item.getFridge().getId()));

		List<FeedFridgeResponse> responses = toResponses(userId, shown, itemsByFridge);
		Long nextCursor = hasNext ? shown.get(shown.size() - 1).getId() : null;
		return new FeedPageResponse(responses, nextCursor);
	}

	public FeedFridgeDetailResponse getFridge(Long userId, Long fridgeId) {
		Fridge fridge = findPublicFridge(fridgeId);
		List<FridgeItem> items = fridgeItemRepository.findAllByFridgeIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
			fridgeId);
		FeedFridgeResponse summary = toResponses(userId, List.of(fridge), Map.of(fridgeId, items)).get(0);
		return new FeedFridgeDetailResponse(summary, FridgeContentsResponse.from(items));
	}

	// 공개하지 않은 냉장고는 있는지도 알 수 없게 없는 냉장고와 같은 오류를 준다
	Fridge findPublicFridge(Long fridgeId) {
		return fridgeRepository.findPublicById(fridgeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_NOT_FOUND));
	}

	// 냉장고마다 쿼리가 나가지 않게 멤버·좋아요·저장을 냉장고 묶음으로 한 번씩 읽는다
	private List<FeedFridgeResponse> toResponses(Long userId, List<Fridge> fridges,
		Map<Long, List<FridgeItem>> itemsByFridge) {
		if (fridges.isEmpty()) {
			return List.of();
		}
		List<Long> fridgeIds = fridges.stream().map(Fridge::getId).toList();
		Map<Long, List<FridgeMember>> membersByFridge = fridgeMemberRepository.findAllWithUserByFridgeIdIn(fridgeIds)
			.stream()
			.collect(Collectors.groupingBy(member -> member.getFridge().getId()));
		Map<Long, Long> likeCounts = feedLikeRepository.countByFridgeIdIn(fridgeIds).stream()
			.collect(Collectors.toMap(FridgeIdCount::getFridgeId, FridgeIdCount::getTotal));
		Set<Long> liked = new HashSet<>(feedLikeRepository.findFridgeIdsByUserIdAndFridgeIdIn(userId, fridgeIds));
		Set<Long> saved = new HashSet<>(feedSaveRepository.findFridgeIdsByUserIdAndFridgeIdIn(userId, fridgeIds));
		LocalDate today = LocalDate.now(clock);

		return fridges.stream()
			.map(fridge -> {
				Long fridgeId = fridge.getId();
				List<FridgeMember> members = membersByFridge.getOrDefault(fridgeId, List.of());
				List<FridgeItem> items = itemsByFridge.getOrDefault(fridgeId, List.of());
				return new FeedFridgeResponse(
					fridgeId,
					fridge.getName(),
					findOwnerNickname(members),
					members.size(),
					items.size(),
					countExpiring(items, today),
					new FeedStorages(
						preview(items, StorageType.FRIDGE),
						preview(items, StorageType.FREEZER),
						preview(items, StorageType.ROOM)),
					likeCounts.getOrDefault(fridgeId, 0L),
					liked.contains(fridgeId),
					saved.contains(fridgeId),
					members.stream().anyMatch(member -> member.getUser().getId().equals(userId)));
			})
			.toList();
	}

	private String findOwnerNickname(List<FridgeMember> members) {
		return members.stream()
			.filter(member -> member.getRole() == MemberRole.OWNER)
			.map(member -> member.getUser().getNickname())
			.findFirst()
			.orElse(null);
	}

	// 이미 지난 재료는 빼고 오늘부터 EXPIRING_DAYS일 안에 끝나는 재료만 센다
	private int countExpiring(List<FridgeItem> items, LocalDate today) {
		LocalDate limit = today.plusDays(EXPIRING_DAYS);
		return (int)items.stream()
			.map(FridgeItem::getExpiresAt)
			.filter(expiresAt -> expiresAt != null && !expiresAt.isBefore(today) && !expiresAt.isAfter(limit))
			.count();
	}

	private FeedStoragePreview preview(List<FridgeItem> items, StorageType storage) {
		List<FridgeItem> inStorage = items.stream()
			.filter(item -> item.getStorage() == storage)
			.sorted(DISPLAY_ORDER)
			.toList();
		return new FeedStoragePreview(inStorage.size(), inStorage.stream()
			.limit(PREVIEW_SIZE)
			.map(FeedItemPreview::from)
			.toList());
	}

}
