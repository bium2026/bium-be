package com.bium.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.notification.entity.NotificationLog;
import com.bium.domain.notification.entity.NotificationStatus;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	@MockitoBean
	private JavaMailSender mailSender;

	private User me;
	private String token;

	@BeforeEach
	void setUp() {
		me = em.merge(new User("login@bium.test", "password", "강희"));
		token = jwtProvider.createAccessToken(me.getId());
	}

	@Test
	void 저장한_적이_없으면_로그인_이메일과_기본값을_보여_준다() throws Exception {
		perform(get("/api/notification-settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("login@bium.test"))
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.notifyTime").value("09:00"))
			.andExpect(jsonPath("$.data.notifyD3").value(true))
			.andExpect(jsonPath("$.data.notifyDday").value(true));
	}

	@Test
	void 이메일과_알림_시각을_저장하고_다시_불러온다() throws Exception {
		save("{\"email\": \" me@bium.test \", \"enabled\": true, \"notifyTime\": \"08:30\", \"notifyD3\": false, \"notifyDday\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").value("me@bium.test"))
			.andExpect(jsonPath("$.data.notifyTime").value("08:30"))
			.andExpect(jsonPath("$.data.notifyD3").value(false));

		perform(get("/api/notification-settings"))
			.andExpect(jsonPath("$.data.email").value("me@bium.test"))
			.andExpect(jsonPath("$.data.enabled").value(true))
			.andExpect(jsonPath("$.data.notifyTime").value("08:30"));
		// 받을 주소를 바꿔도 로그인 이메일은 그대로다
		em.flush();
		em.clear();
		assertThat(em.find(User.class, me.getId()).getEmail()).isEqualTo("login@bium.test");
	}

	@Test
	void 받을_이메일_없이_알림을_켜면_400을_반환한다() throws Exception {
		save("{\"email\": \"  \", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("NOTIFICATION_EMAIL_REQUIRED"));

		// 알림을 끄면 이메일을 지울 수 있고, 다시 불러와도 로그인 이메일로 채우지 않는다
		save("{\"email\": \"\", \"enabled\": false, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.email").doesNotExist())
			.andExpect(jsonPath("$.data.enabled").value(false));
		perform(get("/api/notification-settings"))
			.andExpect(jsonPath("$.data.email").doesNotExist());
		em.flush();
		em.clear();
		assertThat(em.find(User.class, me.getId()).getEmail()).isEqualTo("login@bium.test");
	}

	@Test
	void 알림을_켜고_D_3와_D_day를_모두_끄면_400을_반환하고_알림을_끄면_저장한다() throws Exception {
		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": false, \"notifyDday\": false}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("NOTIFICATION_TIMING_REQUIRED"));
		perform(get("/api/notification-settings"))
			.andExpect(jsonPath("$.data.notifyD3").value(true))
			.andExpect(jsonPath("$.data.notifyDday").value(true));

		save("{\"email\": \"me@bium.test\", \"enabled\": false, \"notifyTime\": \"09:00\", \"notifyD3\": false, \"notifyDday\": false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.enabled").value(false))
			.andExpect(jsonPath("$.data.notifyD3").value(false))
			.andExpect(jsonPath("$.data.notifyDday").value(false));
	}

	@Test
	void 이메일_형식이_틀리거나_시각이_없으면_400을_반환한다() throws Exception {
		save("{\"email\": \"not-an-email\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("이메일 형식이 올바르지 않습니다."));
		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("알림을 받을 시각을 선택해 주세요."));
	}

	@Test
	void 테스트_메일은_저장한_주소로_지금_기준_재료를_담아_보낸다() throws Exception {
		// 받을 주소를 지웠으면 보내지 않는다
		save("{\"email\": \"\", \"enabled\": false, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isOk());
		perform(post("/api/notification-settings/test-mail"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("NOTIFICATION_EMAIL_REQUIRED"));
		verify(mailSender, never()).send(any(SimpleMailMessage.class));

		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isOk());
		Fridge fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, me, MemberRole.OWNER));
		em.persist(FridgeItem.builder().fridge(fridge).name("우유").quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE).section(SectionType.CENTER).sortOrder(0)
			.expiresAt(LocalDate.now(clock)).build());

		perform(post("/api/notification-settings/test-mail"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sentTo").value("me@bium.test"))
			.andExpect(jsonPath("$.data.itemCount").value(1));
		verify(mailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void 테스트_메일도_매일_메일처럼_D_3와_D_day_설정에_맞는_재료만_담는다() throws Exception {
		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": false, \"notifyDday\": true}")
			.andExpect(status().isOk());
		Fridge fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, me, MemberRole.OWNER));
		LocalDate today = LocalDate.now(clock);
		em.persist(FridgeItem.builder().fridge(fridge).name("우유").quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE).section(SectionType.CENTER).sortOrder(0).expiresAt(today).build());
		em.persist(FridgeItem.builder().fridge(fridge).name("두부").quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE).section(SectionType.CENTER).sortOrder(1).expiresAt(today.plusDays(3)).build());

		perform(post("/api/notification-settings/test-mail"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemCount").value(1));
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getText()).contains("우유").doesNotContain("두부");
	}

	@Test
	void 알림을_끈_채_보낸_테스트_메일은_정한_시각에_보낸다고_하지_않는다() throws Exception {
		save("{\"email\": \"me@bium.test\", \"enabled\": false, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isOk());
		perform(post("/api/notification-settings/test-mail")).andExpect(status().isOk());

		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(status().isOk());
		perform(post("/api/notification-settings/test-mail")).andExpect(status().isOk());

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(2)).send(captor.capture());
		assertThat(captor.getAllValues().get(0).getText())
			.contains("알림이 꺼져 있어 매일 알림 메일은 보내지 않아요.")
			.doesNotContain("정한 시각에 알려 드릴게요");
		assertThat(captor.getAllValues().get(1).getText())
			.contains("정한 시각에 알려 드릴게요")
			.doesNotContain("알림이 꺼져 있어");
	}

	@Test
	void 설정을_저장한_적이_없으면_로그인_이메일로_테스트_메일을_보낸다() throws Exception {
		perform(post("/api/notification-settings/test-mail"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sentTo").value("login@bium.test"));
		verify(mailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void 테스트_메일을_보내지_못하면_502를_반환하고_실패를_기록한다() throws Exception {
		willThrow(new MailSendException("SMTP 연결 실패")).given(mailSender).send(any(SimpleMailMessage.class));

		perform(post("/api/notification-settings/test-mail"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error.code").value("NOTIFICATION_MAIL_FAILED"));
		NotificationLog log = em.createQuery("select l from NotificationLog l", NotificationLog.class).getSingleResult();
		assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAIL);
	}

	@Test
	void 예약_알림_메일을_보내지_못한_때를_보여_주고_테스트_메일을_보내지_못한_것은_적지_않는다() throws Exception {
		save("{\"email\": \"me@bium.test\", \"enabled\": true, \"notifyTime\": \"09:00\", \"notifyD3\": true, \"notifyDday\": true}")
			.andExpect(jsonPath("$.data.lastFailedAt").value(nullValue()));
		willThrow(new MailSendException("SMTP 연결 실패")).given(mailSender).send(any(SimpleMailMessage.class));
		perform(post("/api/notification-settings/test-mail")).andExpect(status().isBadGateway());
		perform(get("/api/notification-settings"))
			.andExpect(jsonPath("$.data.lastFailedAt").value(nullValue()));

		LocalDate today = LocalDate.now(clock);
		em.createQuery("update NotificationSetting s set s.lastFailedAt = :failedAt")
			.setParameter("failedAt", today.atTime(9, 5))
			.executeUpdate();
		em.clear();
		perform(get("/api/notification-settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.lastFailedAt").value(startsWith(today + "T09:05")));
	}

	@Test
	void 로그인하지_않으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/notification-settings")).andExpect(status().isUnauthorized());
	}

	private ResultActions save(String body) throws Exception {
		return perform(put("/api/notification-settings").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
