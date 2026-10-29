package com.bium.domain.ingredient.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 재료를 칸 안의 원하는 위치에 놓고, 그 칸 재료의 순번을 0부터 다시 매긴다. 다른 칸에서 옮겨 오면 원래 칸도 다시 매긴다.
 * 두 요청이 같은 칸의 순번을 동시에 매기지 않도록, 부르는 쪽에서 냉장고 행을 먼저 잠가야 한다.
 * 교착 상태를 막으려고 잠그는 순서는 언제나 냉장고 → 옮길 재료(장본 음식) → 원래 칸의 재료 → 놓을 칸의 재료다.
 */
@Component
@RequiredArgsConstructor
public class FridgeItemArranger {

	private final FridgeItemRepository fridgeItemRepository;

	// index가 칸에 있는 재료 수보다 크면 맨 뒤에 놓는다
	public void place(FridgeItem item, StorageType storage, SectionType section, int index) {
		Long fridgeId = item.getFridge().getId();
		// 장본 음식에서 새로 만든 재료(id 없음)는 원래 칸이 없다
		boolean leavesSection = item.getId() != null
			&& (item.getStorage() != storage || item.getSection() != section);
		if (leavesSection) {
			List<FridgeItem> remaining = sectionItemsWithout(item, fridgeId, item.getStorage(), item.getSection());
			renumber(remaining);
		}

		List<FridgeItem> sectionItems = sectionItemsWithout(item, fridgeId, storage, section);
		item.moveTo(storage, section);
		sectionItems.add(Math.min(index, sectionItems.size()), item);
		renumber(sectionItems);
	}

	// 같은 칸 안에서 순서만 바꾸면 자기 자신이 목록에 있으므로 빼고 돌려준다
	private List<FridgeItem> sectionItemsWithout(FridgeItem item, Long fridgeId, StorageType storage,
		SectionType section) {
		List<FridgeItem> sectionItems = new ArrayList<>(
			fridgeItemRepository.findAllInSectionForUpdate(fridgeId, storage, section));
		sectionItems.removeIf(other -> Objects.equals(other.getId(), item.getId()));
		return sectionItems;
	}

	private void renumber(List<FridgeItem> sectionItems) {
		for (int sortOrder = 0; sortOrder < sectionItems.size(); sortOrder++) {
			sectionItems.get(sortOrder).changeSortOrder(sortOrder);
		}
	}

}
