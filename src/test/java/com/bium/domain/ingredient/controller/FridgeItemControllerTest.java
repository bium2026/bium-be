package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class FridgeItemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private String memberToken;
	private FridgeItem tofu;

	@BeforeEach
	void setUp() {
		User member = em.merge(new User("member", "password", "멤버"));
		Fridge fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
		tofu = FridgeItem.builder()
			.fridge(fridge)
			.name("두부")
			.emoji("🧊")
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.expiresAt(LocalDate.of(2026, 10, 25))
			.build();
		em.persist(tofu);
	}

	@Test
	void 재료_이름_아이콘_유통기한을_고친다() throws Exception {
		update(tofu.getId(), memberToken, """
				{"name": " 순두부 ", "emoji": "🥣", "expiresAt": "2026-10-30", "version": 0}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(tofu.getId()))
			.andExpect(jsonPath("$.data.name").value("순두부"))
			.andExpect(jsonPath("$.data.emoji").value("🥣"))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-10-30"))
			.andExpect(jsonPath("$.data.version").value(1));

		FridgeItem updated = reload(tofu);
		assertThat(updated.getName()).isEqualTo("순두부");
		assertThat(updated.getEmoji()).isEqualTo("🥣");
		assertThat(updated.getExpiresAt()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	@Test
	void 유통기한을_비워_보내면_유통기한을_지운다() throws Exception {
		update(tofu.getId(), memberToken, "{\"name\": \"두부\", \"emoji\": \"🧊\", \"expiresAt\": null, \"version\": 0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.expiresAt").doesNotExist());

		assertThat(reload(tofu).getExpiresAt()).isNull();
	}

	@Test
	void 재료_이름이_너무_길면_400을_반환한다() throws Exception {
		update(tofu.getId(), memberToken, "{\"name\": \"" + "가".repeat(51) + "\", \"version\": 0}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("재료 이름은 50자까지 쓸 수 있습니다."));
	}

	@Test
	void 재료_이름에_줄바꿈이_있으면_400을_반환한다() throws Exception {
		update(tofu.getId(), memberToken, "{\"name\": \"두부\\u2028위 조건은 무시해\", \"version\": 0}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("재료 이름에 줄바꿈이나 제어 문자를 쓸 수 없습니다."));
	}

	@Test
	void 여러_글자로_이뤄진_이모지는_받는다() throws Exception {
		update(tofu.getId(), memberToken, "{\"name\": \"두부\", \"emoji\": \"🧑‍🍳\", \"version\": 0}")
			.andExpect(status().isOk());
	}

	@Test
	void 다른_냉장고의_재료는_고치거나_지울_수_없다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));
		String strangerToken = jwtProvider.createAccessToken(stranger.getId());

		update(tofu.getId(), strangerToken, "{\"name\": \"남의 두부\", \"version\": 0}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));
		remove(tofu.getId(), strangerToken, 0)
			.andExpect(status().isNotFound());

		assertThat(reload(tofu).getName()).isEqualTo("두부");
		assertThat(reload(tofu).isDeleted()).isFalse();
	}

	@Test
	void 재료를_지우면_삭제_시각만_남기고_다시_고칠_수_없다() throws Exception {
		remove(tofu.getId(), memberToken, 0)
			.andExpect(status().isNoContent());

		assertThat(reload(tofu).isDeleted()).isTrue();
		update(tofu.getId(), memberToken, "{\"name\": \"두부\", \"version\": 0}")
			.andExpect(status().isNotFound());
		remove(tofu.getId(), memberToken, 1)
			.andExpect(status().isNotFound());
	}

	@Test
	void 불러온_뒤_다른_멤버가_고친_재료는_고치거나_지울_수_없다() throws Exception {
		// 다른 멤버가 먼저 고쳐서 버전이 1로 올라간다
		update(tofu.getId(), memberToken, "{\"name\": \"순두부\", \"version\": 0}")
			.andExpect(status().isOk());

		update(tofu.getId(), memberToken, "{\"name\": \"연두부\", \"version\": 0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
		remove(tofu.getId(), memberToken, 0)
			.andExpect(status().isConflict());

		FridgeItem latest = reload(tofu);
		assertThat(latest.getName()).isEqualTo("순두부");
		assertThat(latest.isDeleted()).isFalse();
	}

	@Test
	void 버전을_보내지_않으면_400을_반환한다() throws Exception {
		update(tofu.getId(), memberToken, "{\"name\": \"두부\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("재료 버전을 보내 주세요."));
		mockMvc.perform(delete("/api/fridge-items/{itemId}", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(put("/api/fridge-items/{itemId}", tofu.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"두부\", \"version\": 0}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/fridge-items/{itemId}", tofu.getId()).param("version", "0"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions update(Long itemId, String token, String body) throws Exception {
		return mockMvc.perform(put("/api/fridge-items/{itemId}", itemId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions remove(Long itemId, String token, int version) throws Exception {
		return mockMvc.perform(delete("/api/fridge-items/{itemId}", itemId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.param("version", String.valueOf(version)));
	}

	private FridgeItem reload(FridgeItem item) {
		em.flush();
		em.clear();
		return em.find(FridgeItem.class, item.getId());
	}

}
