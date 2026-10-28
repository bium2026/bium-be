package com.bium.domain.shopping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.activity.entity.ActivityAction;
import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.shopping.entity.PurchaseSource;
import com.bium.domain.shopping.entity.PurchasedItem;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchasedItemMoveControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private User member;
	private String memberToken;
	private PurchasedItem tofu;

	@BeforeEach
	void setUp() {
		member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
		tofu = PurchasedItem.builder()
			.fridge(fridge)
			.user(member)
			.name("두부")
			.emoji("🧊")
			.storage(StorageType.FRIDGE)
			.purchasedAt(LocalDate.of(2026, 10, 27))
			.expiresAt(LocalDate.of(2026, 11, 3))
			.source(PurchaseSource.MANUAL)
			.build();
		em.persist(tofu);
	}

	@Test
	void 장본_음식을_놓은_칸의_위치에_같은_이름_아이콘_유통기한으로_넣는다() throws Exception {
		fridgeItem("계란", 0);
		fridgeItem("버터", 1);

		move(tofu, memberToken, "FRIDGE", "LEFT_DOOR", 1)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.name").value("두부"))
			.andExpect(jsonPath("$.data.emoji").value("🧊"))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-11-03"))
			.andExpect(jsonPath("$.data.version").value(0));

		em.flush();
		em.clear();
		PurchasedItem moved = em.find(PurchasedItem.class, tofu.getId());
		assertThat(moved.getMovedAt()).isNotNull();
		FridgeItem item = moved.getFridgeItem();
		assertThat(item.getStorage()).isEqualTo(StorageType.FRIDGE);
		assertThat(item.getSection()).isEqualTo(SectionType.LEFT_DOOR);
		assertThat(item.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(item.getPurchasedAt()).isEqualTo(LocalDate.of(2026, 10, 27));
		assertThat(em.createQuery("select i.name from FridgeItem i where i.section = :section order by i.sortOrder, i.id",
				String.class)
			.setParameter("section", SectionType.LEFT_DOOR)
			.getResultList()).containsExactly("계란", "두부", "버터");
		assertThat(em.createQuery("select count(l) from ActivityLog l where l.action = :action", Long.class)
			.setParameter("action", ActivityAction.ADD)
			.getSingleResult()).isEqualTo(1);
	}

	@Test
	void 옮긴_장본_음식은_목록에서_빠지고_다시_옮길_수_없다() throws Exception {
		move(tofu, memberToken, "FRIDGE", "CENTER", 0)
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/fridges/{fridgeId}/purchased-items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0));
		move(tofu, memberToken, "FRIDGE", "CENTER", 0)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PURCHASED_ITEM_NOT_FOUND"));
		assertThat(countFridgeItems()).isEqualTo(1);
	}

	@Test
	void 멤버가_아니면_장본_음식을_옮길_수_없다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));

		move(tofu, jwtProvider.createAccessToken(stranger.getId()), "FRIDGE", "CENTER", 0)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PURCHASED_ITEM_NOT_FOUND"));
		assertThat(countFridgeItems()).isZero();
	}

	@Test
	void 놓을_위치를_보내지_않으면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/api/purchased-items/{purchasedItemId}/move", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"storage\": \"FRIDGE\", \"section\": \"CENTER\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("놓을 위치를 보내 주세요."));
	}

	private void fridgeItem(String name, int sortOrder) {
		em.persist(FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.LEFT_DOOR)
			.sortOrder(sortOrder)
			.build());
	}

	private long countFridgeItems() {
		return em.createQuery("select count(i) from FridgeItem i", Long.class).getSingleResult();
	}

	private ResultActions move(PurchasedItem item, String token, String storage, String section, int index)
		throws Exception {
		return mockMvc.perform(post("/api/purchased-items/{purchasedItemId}/move", item.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"storage\": \"" + storage + "\", \"section\": \"" + section + "\", \"index\": " + index + "}"));
	}

}
