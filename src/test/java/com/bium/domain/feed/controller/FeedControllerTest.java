package com.bium.domain.feed.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.feed.entity.FeedLike;
import com.bium.domain.feed.entity.FeedSave;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	private User owner;
	private User member;
	private User viewer;
	private String memberToken;
	private String viewerToken;
	private LocalDate today;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "강희"));
		member = em.merge(new User("member", "password", "선재"));
		viewer = em.merge(new User("viewer", "password", "윤호"));
		memberToken = jwtProvider.createAccessToken(member.getId());
		viewerToken = jwtProvider.createAccessToken(viewer.getId());
		today = LocalDate.now(clock);
	}

	@Test
	void 공개한_냉장고만_최근에_만든_순서로_보여_준다() throws Exception {
		Fridge older = fridge("먼저 만든 냉장고", owner, true);
		fridge("숨긴 냉장고", owner, false);
		Fridge newer = fridge("나중에 만든 냉장고", viewer, true);

		feed(viewerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridges[*].fridgeId").value(contains(
				newer.getId().intValue(), older.getId().intValue())))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	void 냉장고_카드에_주인_멤버_수_재료_수_임박_재료_수와_구역별_미리보기를_담는다() throws Exception {
		Fridge shared = fridge("강희의 냉장고", owner, true);
		em.persist(new FridgeMember(shared, member, MemberRole.MEMBER));
		// 냉장실: 선반 5개 + 왼쪽 문칸 3개, 미리보기는 선반부터 6개
		for (int i = 0; i < 5; i++) {
			item(shared, "선반" + i, StorageType.FRIDGE, SectionType.CENTER, i, null);
		}
		item(shared, "문칸0", StorageType.FRIDGE, SectionType.LEFT_DOOR, 0, today);
		item(shared, "문칸1", StorageType.FRIDGE, SectionType.LEFT_DOOR, 1, today.plusDays(3));
		item(shared, "문칸2", StorageType.FRIDGE, SectionType.LEFT_DOOR, 2, today.plusDays(4));
		item(shared, "만두", StorageType.FREEZER, SectionType.CENTER, 0, today.minusDays(1));
		FridgeItem deleted = item(shared, "지운 재료", StorageType.ROOM, SectionType.CENTER, 0, today);
		deleted.delete(LocalDateTime.now(clock));

		feed(viewerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridges.length()").value(1))
			.andExpect(jsonPath("$.data.fridges[0].name").value("강희의 냉장고"))
			.andExpect(jsonPath("$.data.fridges[0].ownerNickname").value("강희"))
			.andExpect(jsonPath("$.data.fridges[0].memberCount").value(2))
			.andExpect(jsonPath("$.data.fridges[0].itemCount").value(9))
			// 오늘과 3일 뒤까지만 센다. 4일 뒤와 이미 지난 재료, 지운 재료는 세지 않는다
			.andExpect(jsonPath("$.data.fridges[0].expiringCount").value(2))
			.andExpect(jsonPath("$.data.fridges[0].storages.fridge.count").value(8))
			.andExpect(jsonPath("$.data.fridges[0].storages.fridge.items[*].name").value(contains(
				"선반0", "선반1", "선반2", "선반3", "선반4", "문칸0")))
			.andExpect(jsonPath("$.data.fridges[0].storages.fridge.items[0].emoji").value("🥬"))
			.andExpect(jsonPath("$.data.fridges[0].storages.freezer.count").value(1))
			.andExpect(jsonPath("$.data.fridges[0].storages.room.count").value(0))
			.andExpect(jsonPath("$.data.fridges[0].storages.room.items.length()").value(0))
			.andExpect(jsonPath("$.data.fridges[0].mine").value(false));

		// 멤버에게는 내 냉장고로 알려 준다
		feed(memberToken)
			.andExpect(jsonPath("$.data.fridges[0].mine").value(true));
	}

	@Test
	void 열_개씩_나눠_주고_nextCursor로_다음_페이지를_이어_받는다() throws Exception {
		List<Fridge> fridges = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			fridges.add(fridge("냉장고" + i, owner, true));
		}
		Long tenth = fridges.get(2).getId();

		feed(viewerToken)
			.andExpect(jsonPath("$.data.fridges.length()").value(10))
			.andExpect(jsonPath("$.data.fridges[0].fridgeId").value(fridges.get(11).getId()))
			.andExpect(jsonPath("$.data.nextCursor").value(tenth));

		perform(get("/api/feed/fridges").param("cursor", String.valueOf(tenth)), viewerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridges[*].fridgeId").value(contains(
				fridges.get(1).getId().intValue(), fridges.get(0).getId().intValue())))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	void 좋아요_수와_내가_좋아요나_저장했는지를_알려_준다() throws Exception {
		Fridge fridge = fridge("강희의 냉장고", owner, true);
		em.persist(new FeedLike(viewer, fridge));
		em.persist(new FeedLike(member, fridge));
		em.persist(new FeedSave(viewer, fridge));

		feed(viewerToken)
			.andExpect(jsonPath("$.data.fridges[0].likeCount").value(2))
			.andExpect(jsonPath("$.data.fridges[0].liked").value(true))
			.andExpect(jsonPath("$.data.fridges[0].saved").value(true));

		User other = em.merge(new User("other", "password", "다른 사람"));
		feed(jwtProvider.createAccessToken(other.getId()))
			.andExpect(jsonPath("$.data.fridges[0].likeCount").value(2))
			.andExpect(jsonPath("$.data.fridges[0].liked").value(false))
			.andExpect(jsonPath("$.data.fridges[0].saved").value(false));
	}

	@Test
	void saved가_true면_저장한_냉장고_중_지금도_공개된_냉장고만_준다() throws Exception {
		Fridge savedPublic = fridge("저장한 냉장고", owner, true);
		Fridge savedThenHidden = fridge("저장한 뒤 숨긴 냉장고", owner, true);
		fridge("저장하지 않은 냉장고", owner, true);
		em.persist(new FeedSave(viewer, savedPublic));
		em.persist(new FeedSave(viewer, savedThenHidden));
		savedThenHidden.changeVisibility(false);

		perform(get("/api/feed/fridges").param("saved", "true"), viewerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridges[*].fridgeId").value(contains(savedPublic.getId().intValue())))
			.andExpect(jsonPath("$.data.fridges[0].saved").value(true));
	}

	@Test
	void 공개한_냉장고를_구경하면_칸별_재료를_모두_준다() throws Exception {
		Fridge fridge = fridge("강희의 냉장고", owner, true);
		item(fridge, "우유", StorageType.FRIDGE, SectionType.LEFT_DOOR, 0, today.plusDays(2));
		item(fridge, "두부", StorageType.FRIDGE, SectionType.CENTER, 1, null);
		item(fridge, "계란", StorageType.FRIDGE, SectionType.CENTER, 0, null);
		item(fridge, "만두", StorageType.FREEZER, SectionType.CENTER, 0, null);

		perform(get("/api/feed/fridges/{fridgeId}", fridge.getId()), viewerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.fridgeId").value(fridge.getId()))
			.andExpect(jsonPath("$.data.fridge.itemCount").value(4))
			.andExpect(jsonPath("$.data.fridge.expiringCount").value(1))
			.andExpect(jsonPath("$.data.contents.fridge.center[*].name").value(contains("계란", "두부")))
			.andExpect(jsonPath("$.data.contents.fridge.leftDoor[*].name").value(contains("우유")))
			.andExpect(jsonPath("$.data.contents.freezer.center[*].name").value(contains("만두")))
			.andExpect(jsonPath("$.data.contents.room.center.length()").value(0));
	}

	@Test
	void 공개하지_않은_냉장고는_멤버라도_피드에서_찾을_수_없다() throws Exception {
		Fridge hidden = fridge("숨긴 냉장고", owner, false);
		em.persist(new FridgeMember(hidden, member, MemberRole.MEMBER));

		perform(get("/api/feed/fridges/{fridgeId}", hidden.getId()), memberToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/feed/fridges"))
			.andExpect(status().isUnauthorized());
	}

	private Fridge fridge(String name, User fridgeOwner, boolean isPublic) {
		Fridge fridge = em.merge(new Fridge(name));
		em.persist(new FridgeMember(fridge, fridgeOwner, MemberRole.OWNER));
		fridge.changeVisibility(isPublic);
		return fridge;
	}

	private FridgeItem item(Fridge fridge, String name, StorageType storage, SectionType section, int sortOrder,
		LocalDate expiresAt) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.emoji("🥬")
			.quantity(BigDecimal.ONE)
			.storage(storage)
			.section(section)
			.sortOrder(sortOrder)
			.expiresAt(expiresAt)
			.build();
		em.persist(item);
		return item;
	}

	private ResultActions feed(String token) throws Exception {
		return perform(get("/api/feed/fridges"), token);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
