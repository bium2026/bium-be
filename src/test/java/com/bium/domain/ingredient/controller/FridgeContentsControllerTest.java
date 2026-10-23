package com.bium.domain.ingredient.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
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
class FridgeContentsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private String memberToken;

	@BeforeEach
	void setUp() {
		User member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
	}

	@Test
	void 재료를_보관_구역과_칸별로_나누고_칸_안_순서대로_돌려준다() throws Exception {
		FridgeItem carrot = item("당근", "🥕", StorageType.FRIDGE, SectionType.CENTER, 1);
		FridgeItem tofu = item("두부", "🧊", StorageType.FRIDGE, SectionType.CENTER, 0);
		FridgeItem egg = item("계란", "🥚", StorageType.FRIDGE, SectionType.LEFT_DOOR, 0);
		FridgeItem cheese = item("치즈", "🧀", StorageType.FRIDGE, SectionType.RIGHT_DOOR, 0);
		FridgeItem dumpling = item("만두", "🥟", StorageType.FREEZER, SectionType.CENTER, 0);
		FridgeItem onion = item("양파", "🧅", StorageType.ROOM, SectionType.CENTER, 0);

		getContents(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.center.length()").value(2))
			.andExpect(jsonPath("$.data.fridge.center[0].id").value(tofu.getId()))
			.andExpect(jsonPath("$.data.fridge.center[0].name").value("두부"))
			.andExpect(jsonPath("$.data.fridge.center[0].emoji").value("🧊"))
			.andExpect(jsonPath("$.data.fridge.center[0].expiresAt").value("2026-10-30"))
			.andExpect(jsonPath("$.data.fridge.center[0].version").value(0))
			.andExpect(jsonPath("$.data.fridge.center[1].id").value(carrot.getId()))
			.andExpect(jsonPath("$.data.fridge.leftDoor[0].id").value(egg.getId()))
			.andExpect(jsonPath("$.data.fridge.rightDoor[0].id").value(cheese.getId()))
			.andExpect(jsonPath("$.data.freezer.center[0].id").value(dumpling.getId()))
			.andExpect(jsonPath("$.data.freezer.leftDoor.length()").value(0))
			.andExpect(jsonPath("$.data.room.center[0].id").value(onion.getId()))
			.andExpect(jsonPath("$.data.room.rightDoor.length()").value(0));
	}

	@Test
	void 지운_재료는_돌려주지_않는다() throws Exception {
		FridgeItem tofu = item("두부", "🧊", StorageType.FRIDGE, SectionType.CENTER, 0);
		FridgeItem milk = item("우유", "🥛", StorageType.FRIDGE, SectionType.CENTER, 1);
		milk.delete(LocalDateTime.of(2026, 10, 20, 12, 0));
		em.flush();

		getContents(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.center.length()").value(1))
			.andExpect(jsonPath("$.data.fridge.center[0].id").value(tofu.getId()));
	}

	@Test
	void 재료가_없으면_모든_칸을_빈_배열로_돌려준다() throws Exception {
		getContents(fridge.getId(), memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.center.length()").value(0))
			.andExpect(jsonPath("$.data.fridge.leftDoor.length()").value(0))
			.andExpect(jsonPath("$.data.fridge.rightDoor.length()").value(0))
			.andExpect(jsonPath("$.data.freezer.center.length()").value(0))
			.andExpect(jsonPath("$.data.room.center.length()").value(0));
	}

	@Test
	void 멤버가_아니면_냉장고가_없을_때와_같은_404를_반환한다() throws Exception {
		item("두부", "🧊", StorageType.FRIDGE, SectionType.CENTER, 0);
		User stranger = em.merge(new User("stranger", "password", "남"));

		getContents(fridge.getId(), jwtProvider.createAccessToken(stranger.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		getContents(fridge.getId() + 1000, memberToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridge.getId()))
			.andExpect(status().isUnauthorized());
	}

	private FridgeItem item(String name, String emoji, StorageType storage, SectionType section, int sortOrder) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.emoji(emoji)
			.quantity(BigDecimal.ONE)
			.storage(storage)
			.section(section)
			.sortOrder(sortOrder)
			.expiresAt(LocalDate.of(2026, 10, 30))
			.build();
		em.persist(item);
		return item;
	}

	private ResultActions getContents(Long fridgeId, String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
