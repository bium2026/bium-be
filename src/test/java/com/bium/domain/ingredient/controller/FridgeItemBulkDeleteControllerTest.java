package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FridgeItemBulkDeleteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private Fridge fridge;
	private String memberToken;
	private FridgeItem tofu;
	private FridgeItem milk;

	@BeforeEach
	void setUp() {
		User member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
		tofu = item(fridge, "두부");
		milk = item(fridge, "우유");
	}

	@Test
	void 보낸_재료를_모두_지우고_재료마다_활동_기록을_남긴다() throws Exception {
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + "," + entry(milk, 0))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.deletedItemIds[0]").value(tofu.getId()))
			.andExpect(jsonPath("$.data.deletedItemIds[1]").value(milk.getId()));

		assertThat(reload(tofu).isDeleted()).isTrue();
		assertThat(reload(milk).isDeleted()).isTrue();
		assertThat(countDeleteLogs()).isEqualTo(2);
	}

	@Test
	void 같은_재료를_두_번_보내도_한_번만_지운다() throws Exception {
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + "," + entry(tofu, 0))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.deletedItemIds.length()").value(1));

		assertThat(countDeleteLogs()).isEqualTo(1);
	}

	@Test
	void 그사이_다른_멤버가_고친_재료가_있으면_아무것도_지우지_않는다() throws Exception {
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + "," + entry(milk, 3))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

		assertThat(reload(tofu).isDeleted()).isFalse();
		assertThat(countDeleteLogs()).isZero();
	}

	@Test
	void 다른_냉장고의_재료나_이미_지운_재료가_있으면_아무것도_지우지_않는다() throws Exception {
		FridgeItem othersItem = item(em.merge(new Fridge("남의 냉장고")), "남의 두부");
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + "," + entry(othersItem, 0))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));

		milk.delete(LocalDateTime.of(2026, 11, 1, 12, 0));
		em.flush();
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + "," + entry(milk, 1))
			.andExpect(status().isNotFound());

		assertThat(reload(tofu).isDeleted()).isFalse();
		assertThat(reload(othersItem).isDeleted()).isFalse();
	}

	@Test
	void 없는_재료가_섞여_있으면_아무것도_지우지_않는다() throws Exception {
		bulkDelete(fridge.getId(), memberToken, entry(tofu, 0) + ",{\"fridgeItemId\": 999999, \"version\": 0}")
			.andExpect(status().isNotFound());

		assertThat(reload(tofu).isDeleted()).isFalse();
	}

	@Test
	void 멤버가_아니면_냉장고가_없을_때와_같은_404를_반환한다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));

		bulkDelete(fridge.getId(), jwtProvider.createAccessToken(stranger.getId()), entry(tofu, 0))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		assertThat(reload(tofu).isDeleted()).isFalse();
	}

	@Test
	void 지울_재료를_보내지_않으면_400을_반환한다() throws Exception {
		bulkDelete(fridge.getId(), memberToken, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("지울 재료를 하나 이상 보내 주세요."));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/items/bulk-delete", fridge.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\": [" + entry(tofu, 0) + "]}"))
			.andExpect(status().isUnauthorized());
	}

	private FridgeItem item(Fridge target, String name) {
		FridgeItem item = FridgeItem.builder()
			.fridge(target)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.expiresAt(LocalDate.of(2026, 10, 30))
			.build();
		em.persist(item);
		return item;
	}

	private long countDeleteLogs() {
		return em.createQuery("select count(l) from ActivityLog l where l.action = :action", Long.class)
			.setParameter("action", ActivityAction.DELETE)
			.getSingleResult();
	}

	private FridgeItem reload(FridgeItem item) {
		em.flush();
		em.clear();
		return em.find(FridgeItem.class, item.getId());
	}

	private String entry(FridgeItem item, int version) {
		return "{\"fridgeItemId\": " + item.getId() + ", \"version\": " + version + "}";
	}

	private ResultActions bulkDelete(Long fridgeId, String token, String items) throws Exception {
		return mockMvc.perform(post("/api/fridges/{fridgeId}/items/bulk-delete", fridgeId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\": [" + items + "]}"));
	}

}
