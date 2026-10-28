package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
class FridgeItemPositionControllerTest {

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
	private FridgeItem egg;

	@BeforeEach
	void setUp() {
		User member = em.merge(new User("member", "password", "멤버"));
		fridge = em.merge(new Fridge("우리 냉장고"));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		memberToken = jwtProvider.createAccessToken(member.getId());
		tofu = item("두부", SectionType.CENTER, 0);
		milk = item("우유", SectionType.CENTER, 1);
		egg = item("계란", SectionType.CENTER, 2);
	}

	@Test
	void 같은_칸_안에서_순서를_바꾸면_버전을_올리지_않는다() throws Exception {
		changePosition(egg, memberToken, "FRIDGE", "CENTER", 0, 0)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(egg.getId()))
			.andExpect(jsonPath("$.data.version").value(0));

		assertThat(centerNames()).containsExactly("계란", "두부", "우유");
		assertThat(reload(tofu).getVersion()).isZero();
		assertThat(countMoveLogs()).isZero();
	}

	@Test
	void 순서가_밀린_재료도_불러올_때_받은_버전으로_고칠_수_있다() throws Exception {
		changePosition(egg, memberToken, "FRIDGE", "CENTER", 0, 0)
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/fridge-items/{itemId}", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"순두부\", \"version\": 0}"))
			.andExpect(status().isOk());
	}

	@Test
	void 다른_칸으로_옮기면_놓은_위치에_넣고_버전을_올리고_활동_기록을_남긴다() throws Exception {
		FridgeItem butter = item("버터", StorageType.FREEZER, SectionType.LEFT_DOOR, 0);
		FridgeItem cheese = item("치즈", StorageType.FREEZER, SectionType.LEFT_DOOR, 1);

		changePosition(milk, memberToken, "FREEZER", "LEFT_DOOR", 1, 0)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(1));

		FridgeItem moved = reload(milk);
		assertThat(moved.getStorage()).isEqualTo(StorageType.FREEZER);
		assertThat(moved.getSection()).isEqualTo(SectionType.LEFT_DOOR);
		assertThat(namesIn(StorageType.FREEZER, SectionType.LEFT_DOOR)).containsExactly("버터", "우유", "치즈");
		assertThat(centerNames()).containsExactly("두부", "계란");
		assertThat(reload(butter).getVersion()).isZero();
		assertThat(reload(cheese).getVersion()).isZero();
		assertThat(countMoveLogs()).isEqualTo(1);
	}

	@Test
	void 다른_칸으로_옮기면_원래_칸의_남은_재료도_0부터_다시_매긴다() throws Exception {
		changePosition(tofu, memberToken, "FREEZER", "CENTER", 0, 0)
			.andExpect(status().isOk());

		assertThat(reload(milk).getSortOrder()).isZero();
		assertThat(reload(egg).getSortOrder()).isEqualTo(1);
		assertThat(reload(milk).getVersion()).isZero();
	}

	@Test
	void 놓을_위치가_칸의_재료_수보다_크면_맨_뒤에_놓는다() throws Exception {
		changePosition(tofu, memberToken, "FRIDGE", "CENTER", 100, 0)
			.andExpect(status().isOk());

		assertThat(centerNames()).containsExactly("우유", "계란", "두부");
	}

	@Test
	void 빈_칸으로_옮기면_첫_재료가_된다() throws Exception {
		changePosition(tofu, memberToken, "ROOM", "RIGHT_DOOR", 3, 0)
			.andExpect(status().isOk());

		assertThat(reload(tofu).getSortOrder()).isZero();
		assertThat(namesIn(StorageType.ROOM, SectionType.RIGHT_DOOR)).containsExactly("두부");
	}

	@Test
	void 그사이_다른_멤버가_고친_재료는_옮기지_않는다() throws Exception {
		changePosition(egg, memberToken, "FREEZER", "CENTER", 0, 3)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

		assertThat(reload(egg).getStorage()).isEqualTo(StorageType.FRIDGE);
	}

	@Test
	void 멤버가_아니거나_지운_재료는_옮길_수_없다() throws Exception {
		User stranger = em.merge(new User("stranger", "password", "남"));
		changePosition(egg, jwtProvider.createAccessToken(stranger.getId()), "FREEZER", "CENTER", 0, 0)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_ITEM_NOT_FOUND"));

		milk.delete(LocalDateTime.of(2026, 10, 20, 12, 0));
		em.flush();
		changePosition(milk, memberToken, "FREEZER", "CENTER", 0, 1)
			.andExpect(status().isNotFound());

		assertThat(reload(egg).getStorage()).isEqualTo(StorageType.FRIDGE);
	}

	@Test
	void 놓을_위치가_음수이거나_칸이_잘못되면_400을_반환한다() throws Exception {
		changePosition(egg, memberToken, "FRIDGE", "CENTER", -1, 0)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("놓을 위치는 0 이상이어야 합니다."));
		changePosition(egg, memberToken, "FRIDGE", "TOP_SHELF", 0, 0)
			.andExpect(status().isBadRequest());
	}

	private FridgeItem item(String name, SectionType section, int sortOrder) {
		return item(name, StorageType.FRIDGE, section, sortOrder);
	}

	private FridgeItem item(String name, StorageType storage, SectionType section, int sortOrder) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(storage)
			.section(section)
			.sortOrder(sortOrder)
			.build();
		em.persist(item);
		return item;
	}

	private List<String> centerNames() {
		return namesIn(StorageType.FRIDGE, SectionType.CENTER);
	}

	private List<String> namesIn(StorageType storage, SectionType section) {
		em.flush();
		em.clear();
		return em.createQuery("select i.name from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage"
				+ " and i.section = :section and i.deletedAt is null order by i.sortOrder, i.id", String.class)
			.setParameter("fridgeId", fridge.getId())
			.setParameter("storage", storage)
			.setParameter("section", section)
			.getResultList();
	}

	private long countMoveLogs() {
		return em.createQuery("select count(l) from ActivityLog l where l.action = :action", Long.class)
			.setParameter("action", ActivityAction.MOVE)
			.getSingleResult();
	}

	private FridgeItem reload(FridgeItem item) {
		em.flush();
		em.clear();
		return em.find(FridgeItem.class, item.getId());
	}

	private ResultActions changePosition(FridgeItem item, String token, String storage, String section, int index,
		int version) throws Exception {
		return mockMvc.perform(patch("/api/fridge-items/{itemId}/position", item.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"storage\": \"" + storage + "\", \"section\": \"" + section + "\", \"index\": " + index
				+ ", \"version\": " + version + "}"));
	}

}
