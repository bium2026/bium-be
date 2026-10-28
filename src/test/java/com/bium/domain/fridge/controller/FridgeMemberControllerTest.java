package com.bium.domain.fridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeInvite;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.fridge.repository.FridgeMemberRepository;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FridgeMemberControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private FridgeMemberRepository fridgeMemberRepository;

	@Autowired
	private Clock clock;

	private Fridge fridge;
	private User owner;
	private User firstMember;
	private User secondMember;
	private User stranger;
	private String ownerToken;
	private String memberToken;
	private String strangerToken;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "윤호"));
		firstMember = em.merge(new User("first", "password", "선재"));
		secondMember = em.merge(new User("second", "password", "강희"));
		stranger = em.merge(new User("stranger", "password", "남"));
		fridge = em.merge(new Fridge("윤호의 냉장고"));
		// 소유자보다 먼저 들어온 멤버가 있어도 목록에서는 소유자가 맨 앞이어야 한다
		em.persist(new FridgeMember(fridge, firstMember, MemberRole.MEMBER));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, secondMember, MemberRole.MEMBER));
		ownerToken = jwtProvider.createAccessToken(owner.getId());
		memberToken = jwtProvider.createAccessToken(firstMember.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 멤버_목록은_소유자를_맨_앞에_두고_나머지는_참여한_순서로_보여_준다() throws Exception {
		getMembers(memberToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].userId").value(owner.getId()))
			.andExpect(jsonPath("$.data[0].nickname").value("윤호"))
			.andExpect(jsonPath("$.data[0].role").value("OWNER"))
			.andExpect(jsonPath("$.data[0].joinedAt").exists())
			.andExpect(jsonPath("$.data[0].isMe").value(false))
			.andExpect(jsonPath("$.data[1].nickname").value("선재"))
			.andExpect(jsonPath("$.data[1].role").value("MEMBER"))
			.andExpect(jsonPath("$.data[1].isMe").value(true))
			.andExpect(jsonPath("$.data[2].nickname").value("강희"))
			.andExpect(jsonPath("$.data[2].isMe").value(false));
	}

	@Test
	void 멤버가_아니면_목록을_볼_수_없다() throws Exception {
		getMembers(strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 소유자는_멤버를_내보낼_수_있다() throws Exception {
		remove(ownerToken, firstMember)
			.andExpect(status().isNoContent());

		assertThat(isMember(firstMember)).isFalse();
		getMembers(memberToken)
			.andExpect(status().isNotFound());
	}

	@Test
	void 소유자가_아니면_멤버를_내보낼_수_없다() throws Exception {
		remove(memberToken, secondMember)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_OWNER_ONLY"));

		assertThat(isMember(secondMember)).isTrue();
	}

	@Test
	void 소유자는_자기_자신을_내보낼_수_없다() throws Exception {
		remove(ownerToken, owner)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("OWNER_CANNOT_LEAVE"));

		assertThat(isMember(owner)).isTrue();
	}

	@Test
	void 내보내면_그때_쓰던_초대_코드로_다시_들어올_수_없다() throws Exception {
		em.persist(new FridgeInvite(fridge, "K7MX2QPA", owner, LocalDateTime.now(clock).plusHours(1)));

		remove(ownerToken, firstMember)
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/fridges/join")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\": \"K7MX2QPA\"}"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));
		assertThat(isMember(firstMember)).isFalse();
	}

	@Test
	void 냉장고_멤버가_아닌_사용자는_내보낼_수_없다() throws Exception {
		remove(ownerToken, stranger)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_MEMBER_NOT_FOUND"));
	}

	@Test
	void 다른_냉장고의_멤버는_내보낼_수_없다() throws Exception {
		Fridge otherFridge = em.merge(new Fridge("남의 냉장고"));
		em.persist(new FridgeMember(otherFridge, stranger, MemberRole.OWNER));
		em.persist(new FridgeMember(otherFridge, secondMember, MemberRole.MEMBER));
		User otherMember = em.merge(new User("other", "password", "다른 멤버"));
		em.persist(new FridgeMember(otherFridge, otherMember, MemberRole.MEMBER));

		// 내 냉장고 주소로 다른 냉장고에만 있는 멤버를 내보내려 하면 멤버가 아니라고 본다
		remove(ownerToken, otherMember)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_MEMBER_NOT_FOUND"));
		// 내가 멤버가 아닌 냉장고 주소로는 그 냉장고가 있는지도 알 수 없다
		mockMvc.perform(delete("/api/fridges/{fridgeId}/members/{userId}", otherFridge.getId(), secondMember.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));

		assertThat(fridgeMemberRepository.existsByFridgeIdAndUserId(otherFridge.getId(), otherMember.getId())).isTrue();
		assertThat(fridgeMemberRepository.existsByFridgeIdAndUserId(otherFridge.getId(), secondMember.getId())).isTrue();
	}

	@Test
	void 멤버는_냉장고를_나갈_수_있다() throws Exception {
		leave(memberToken)
			.andExpect(status().isNoContent());

		assertThat(isMember(firstMember)).isFalse();
		assertThat(isMember(secondMember)).isTrue();
	}

	@Test
	void 소유자는_냉장고를_나갈_수_없다() throws Exception {
		leave(ownerToken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("OWNER_CANNOT_LEAVE"));

		assertThat(isMember(owner)).isTrue();
	}

	@Test
	void 멤버가_아니면_나갈_수_없다() throws Exception {
		leave(strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/fridges/{fridgeId}/members", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/fridges/{fridgeId}/members/me", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/fridges/{fridgeId}/members/{userId}", fridge.getId(), firstMember.getId()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions getMembers(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/members", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions remove(String token, User target) throws Exception {
		return mockMvc.perform(delete("/api/fridges/{fridgeId}/members/{userId}", fridge.getId(), target.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions leave(String token) throws Exception {
		return mockMvc.perform(delete("/api/fridges/{fridgeId}/members/me", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private boolean isMember(User user) {
		em.flush();
		em.clear();
		return fridgeMemberRepository.existsByFridgeIdAndUserId(fridge.getId(), user.getId());
	}

}
