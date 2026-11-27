package com.bium.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.dto.SignupRequest;
import com.bium.domain.auth.service.AuthService;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.notification.entity.NotificationLog;
import com.bium.domain.notification.entity.NotificationSetting;
import com.bium.domain.notification.entity.NotificationStatus;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class ExpiryNotificationServiceTest {

	private static final LocalTime NINE = LocalTime.of(9, 0);

	@Autowired
	private ExpiryNotificationService notificationService;

	@Autowired
	private AuthService authService;

	@Autowired
	private EntityManager em;

	@Autowired
	private Clock clock;

	@MockitoBean
	private JavaMailSender mailSender;

	private User me;
	private Fridge myFridge;
	private NotificationSetting setting;
	private LocalDate today;
	private LocalDateTime tenOClock;

	@BeforeEach
	void setUp() {
		today = LocalDate.now(clock);
		tenOClock = today.atTime(10, 0);
		// 로그인 이메일과 다른 주소로 받도록 해서 알림 설정의 주소로 보내는지 본다
		me = em.merge(new User("login@bium.test", "password", "강희"));
		myFridge = fridge("강희의 냉장고", me, MemberRole.OWNER);
		setting = new NotificationSetting(me, NINE);
		setting.changeEmail("me@bium.test");
		em.persist(setting);
	}

	@Test
	void 알림_시각이_지나면_내가_속한_냉장고의_D_day와_D_3_재료를_메일로_보낸다() {
		User other = em.merge(new User("other", "password", "선재"));
		Fridge shared = fridge("선재의 냉장고", other, MemberRole.OWNER);
		em.persist(new FridgeMember(shared, me, MemberRole.MEMBER));
		Fridge othersOnly = fridge("남의 냉장고", other, MemberRole.OWNER);
		item(myFridge, "우유", today);
		item(shared, "두부", today.plusDays(3));
		item(myFridge, "계란", today.plusDays(1));
		item(myFridge, "당근", today.plusDays(4));
		item(othersOnly, "남의 우유", today);
		item(myFridge, "지운 재료", today).delete(tenOClock);

		assertThat(notificationService.sendDue(tenOClock)).isEqualTo(1);

		SimpleMailMessage mail = sentMail();
		assertThat(mail.getTo()).containsExactly("me@bium.test");
		assertThat(mail.getFrom()).isEqualTo("noreply@bium.test");
		assertThat(mail.getSubject()).isEqualTo("[비움] 유통기한이 다가온 재료 2개");
		assertThat(mail.getText())
			.contains("오늘까지 먹어야 해요 (D-day)", "우유 (강희의 냉장고)", "3일 남았어요 (D-3)", "두부 (선재의 냉장고)")
			.doesNotContain("계란", "당근", "남의 우유", "지운 재료");
		NotificationLog log = singleLog();
		assertThat(log.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
		assertThat(log.getItemCount()).isEqualTo(2);
		assertThat(reloadSetting().getLastSentDate()).isEqualTo(today);
	}

	@Test
	void 가입하고_알림_설정을_저장한_적이_없어도_기본값대로_가입한_이메일로_보낸다() {
		Long userId = authService.signup(new SignupRequest("new@bium.test", "bium2026", "선재")).userId();
		item(fridge("선재의 냉장고", em.find(User.class, userId), MemberRole.OWNER), "우유", today);

		// 기본 알림 시각(9시) 전에는 보내지 않는다
		assertThat(notificationService.sendDue(today.atTime(8, 55))).isZero();
		assertThat(notificationService.sendDue(tenOClock)).isEqualTo(1);

		SimpleMailMessage mail = sentMail();
		assertThat(mail.getTo()).containsExactly("new@bium.test");
		assertThat(mail.getText()).contains("우유 (선재의 냉장고)");
	}

	@Test
	void 같은_날에는_한_번만_보낸다() {
		item(myFridge, "우유", today);

		assertThat(notificationService.sendDue(tenOClock)).isEqualTo(1);
		assertThat(notificationService.sendDue(tenOClock.plusMinutes(5))).isZero();

		verify(mailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void 알림_시각_전이거나_끄거나_받을_이메일이_없으면_보내지_않는다() {
		item(myFridge, "우유", today);

		assertThat(notificationService.sendDue(today.atTime(8, 55))).isZero();
		setting.update(false, NINE, true, true);
		assertThat(notificationService.sendDue(tenOClock)).isZero();
		setting.update(true, NINE, true, true);
		setting.changeEmail(null);
		assertThat(notificationService.sendDue(tenOClock)).isZero();

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void D_3_알림을_끄면_D_day_재료만_보내고_알릴_재료가_없으면_메일을_보내지_않는다() {
		setting.update(true, NINE, false, true);
		item(myFridge, "두부", today.plusDays(3));

		assertThat(notificationService.sendDue(tenOClock)).isZero();
		verify(mailSender, never()).send(any(SimpleMailMessage.class));

		item(myFridge, "우유", today);
		setting = reloadSetting();
		em.createQuery("update NotificationSetting s set s.lastCheckedDate = null").executeUpdate();
		assertThat(notificationService.sendDue(tenOClock)).isEqualTo(1);
		assertThat(sentMail().getText()).contains("우유").doesNotContain("두부");
	}

	@Test
	void 메일을_보내지_못하면_실패로_기록하고_그날은_다시_보내지_않는다() {
		item(myFridge, "우유", today);
		willThrow(new MailSendException("SMTP 연결 실패")).given(mailSender).send(any(SimpleMailMessage.class));

		assertThat(notificationService.sendDue(tenOClock)).isZero();
		assertThat(notificationService.sendDue(tenOClock.plusMinutes(5))).isZero();

		NotificationLog log = singleLog();
		assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAIL);
		assertThat(log.getErrorMessage()).contains("SMTP 연결 실패");
		// 보내지 못한 날은 마지막으로 보낸 날이 아니다
		NotificationSetting reloaded = reloadSetting();
		assertThat(reloaded.getLastCheckedDate()).isEqualTo(today);
		assertThat(reloaded.getLastSentDate()).isNull();
	}

	@Test
	void 알릴_재료가_없거나_D_3와_D_day를_모두_꺼서_보내지_않은_날은_마지막으로_보낸_날을_바꾸지_않는다() {
		item(myFridge, "당근", today.plusDays(4));

		assertThat(notificationService.sendDue(tenOClock)).isZero();
		NotificationSetting reloaded = reloadSetting();
		assertThat(reloaded.getLastCheckedDate()).isEqualTo(today);
		assertThat(reloaded.getLastSentDate()).isNull();

		item(myFridge, "우유", today);
		reloaded.update(true, NINE, false, false);
		em.createQuery("update NotificationSetting s set s.lastCheckedDate = null").executeUpdate();
		assertThat(notificationService.sendDue(tenOClock)).isZero();
		assertThat(reloadSetting().getLastSentDate()).isNull();
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void 예전처럼_마지막으로_보낸_날로_오늘_차례를_잡은_줄은_다시_보내지_않는다() {
		item(myFridge, "우유", today);
		em.createQuery("update NotificationSetting s set s.lastSentDate = :today")
			.setParameter("today", today)
			.executeUpdate();

		assertThat(notificationService.sendDue(tenOClock)).isZero();
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	private SimpleMailMessage sentMail() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		return captor.getValue();
	}

	private NotificationLog singleLog() {
		List<NotificationLog> logs = em.createQuery("select l from NotificationLog l", NotificationLog.class)
			.getResultList();
		assertThat(logs).hasSize(1);
		return logs.get(0);
	}

	private NotificationSetting reloadSetting() {
		em.flush();
		em.clear();
		return em.find(NotificationSetting.class, me.getId());
	}

	private Fridge fridge(String name, User owner, MemberRole role) {
		Fridge fridge = em.merge(new Fridge(name));
		em.persist(new FridgeMember(fridge, owner, role));
		return fridge;
	}

	private FridgeItem item(Fridge fridge, String name, LocalDate expiresAt) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.emoji("🥛")
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.expiresAt(expiresAt)
			.build();
		em.persist(item);
		return item;
	}

}
