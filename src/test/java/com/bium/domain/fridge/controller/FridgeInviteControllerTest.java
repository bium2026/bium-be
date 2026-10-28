package com.bium.domain.fridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
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
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FridgeInviteControllerTest {

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
	private User member;
	private User stranger;
	private String ownerToken;
	private String memberToken;
	private String strangerToken;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "윤호"));
		member = em.merge(new User("member", "password", "선재"));
		stranger = em.merge(new User("stranger", "password", "강희"));
		fridge = em.merge(new Fridge("윤호의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		ownerToken = jwtProvider.createAccessToken(owner.getId());
		memberToken = jwtProvider.createAccessToken(member.getId());
		strangerToken = jwtProvider.createAccessToken(stranger.getId());
	}

	@Test
	void 소유자가_코드를_만들면_24시간_동안_쓸_수_있는_8자_코드를_준다() throws Exception {
		LocalDateTime before = LocalDateTime.now(clock);
		String created = codeOf(createInvite(ownerToken)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.code").value(matchesPattern("[A-HJ-NP-Z2-9]{8}")))
			.andExpect(jsonPath("$.data.expiresAt").exists()));

		FridgeInvite invite = findInvite(created);
		assertThat(invite.getExpiresAt()).isBetween(before.plusHours(24).minusSeconds(1),
			LocalDateTime.now(clock).plusHours(24));
		getActive(ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.code").value(created));
	}

	@Test
	void 새_코드를_만들면_전에_만든_코드로는_참여할_수_없다() throws Exception {
		String oldCode = codeOf(createInvite(ownerToken));
		String newCode = codeOf(createInvite(ownerToken));
		assertThat(newCode).isNotEqualTo(oldCode);

		join(strangerToken, oldCode)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));
		join(strangerToken, newCode)
			.andExpect(status().isOk());
		getActive(ownerToken)
			.andExpect(jsonPath("$.data.code").value(newCode));
	}

	@Test
	void 쓸_수_있는_코드가_없으면_빈_값을_준다() throws Exception {
		getActive(ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());

		em.persist(new FridgeInvite(fridge, "EXPIRED2", owner, LocalDateTime.now(clock).minusMinutes(1)));
		getActive(ownerToken)
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void 소유자가_아니면_코드를_만들거나_볼_수_없다() throws Exception {
		createInvite(memberToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_OWNER_ONLY"));
		getActive(memberToken)
			.andExpect(status().isForbidden());

		// 멤버가 아니면 냉장고가 있는지도 알 수 없게 없는 냉장고와 같은 오류를 준다
		createInvite(strangerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("FRIDGE_NOT_FOUND"));
		getActive(strangerToken)
			.andExpect(status().isNotFound());
	}

	@Test
	void 코드로_참여하면_멤버로_들어가고_냉장고를_알려_준다() throws Exception {
		em.persist(new FridgeInvite(fridge, "K7MX2QPA", owner, LocalDateTime.now(clock).plusHours(1)));

		// 소문자나 앞뒤 공백을 섞어 입력해도 같은 코드로 본다
		join(strangerToken, " k7mx2qpa ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(fridge.getId()))
			.andExpect(jsonPath("$.data.name").value("윤호의 냉장고"))
			.andExpect(jsonPath("$.data.role").value("MEMBER"));

		FridgeMember joined = fridgeMemberRepository.findByFridgeIdAndUserId(fridge.getId(), stranger.getId())
			.orElseThrow();
		assertThat(joined.getRole()).isEqualTo(MemberRole.MEMBER);
	}

	@Test
	void 없는_코드로는_참여할_수_없다() throws Exception {
		join(strangerToken, "NOPE2345")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
	}

	@Test
	void 만료된_코드로는_참여할_수_없다() throws Exception {
		em.persist(new FridgeInvite(fridge, "OLDCODE2", owner, LocalDateTime.now(clock).minusMinutes(1)));

		join(strangerToken, "OLDCODE2")
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));
		assertThat(fridgeMemberRepository.existsByFridgeIdAndUserId(fridge.getId(), stranger.getId())).isFalse();
	}

	@Test
	void 이미_멤버면_다시_참여할_수_없다() throws Exception {
		em.persist(new FridgeInvite(fridge, "K7MX2QPA", owner, LocalDateTime.now(clock).plusHours(1)));

		join(memberToken, "K7MX2QPA")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ALREADY_FRIDGE_MEMBER"));
	}

	@Test
	void 코드를_비워_보내면_400을_반환한다() throws Exception {
		join(strangerToken, " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("초대 코드를 입력해 주세요."));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/fridges/{fridgeId}/invites", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/fridges/{fridgeId}/invites/active", fridge.getId()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/fridges/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\": \"K7MX2QPA\"}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions createInvite(String token) throws Exception {
		return mockMvc.perform(post("/api/fridges/{fridgeId}/invites", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions getActive(String token) throws Exception {
		return mockMvc.perform(get("/api/fridges/{fridgeId}/invites/active", fridge.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions join(String token, String code) throws Exception {
		return mockMvc.perform(post("/api/fridges/join")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\": \"" + code + "\"}"));
	}

	private String codeOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.code");
	}

	private FridgeInvite findInvite(String code) {
		em.flush();
		em.clear();
		return em.createQuery("select i from FridgeInvite i where i.code = :code", FridgeInvite.class)
			.setParameter("code", code)
			.getSingleResult();
	}

}
