package com.bium.domain.notification.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.notification.entity.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	// 알림을 켰고 받을 이메일이 있으며, 알림 시각이 지났는데 오늘 아직 보내지 않은 사용자 중
	// 오늘 아직 차례를 잡지 않았거나, 오늘 잡은 차례가 모두 실패해 maxAttempts번이 되기 전까지 다시 보낼 사용자.
	// lastSentDate도 보는 것은 예전에 lastSentDate로 차례를 잡던 줄이 오늘 이미 보낸 경우를 건너뛰기 위해서다
	@Query("select s from NotificationSetting s join fetch s.user where s.enabled = true and s.email is not null"
		+ " and s.notifyTime <= :now and (s.lastSentDate is null or s.lastSentDate < :today)"
		+ " and (s.lastCheckedDate is null or s.lastCheckedDate < :today"
		+ " or (s.lastCheckedDate = :today and s.sendFailCount > 0 and s.sendAttemptCount = s.sendFailCount"
		+ " and s.sendAttemptCount < :maxAttempts))")
	List<NotificationSetting> findDue(LocalTime now, LocalDate today, int maxAttempts);

	// 오늘 보낼 차례를 먼저 차지한다. 예약 작업이 겹치거나 서버가 여러 대여도 한 요청만 1을 받아 같은 날 두 번 보내지 않는다
	@Modifying
	@Query("update NotificationSetting s set s.lastCheckedDate = :today, s.sendAttemptCount = 1, s.sendFailCount = 0"
		+ " where s.userId = :userId"
		+ " and (s.lastCheckedDate is null or s.lastCheckedDate < :today)"
		+ " and (s.lastSentDate is null or s.lastSentDate < :today)")
	int claimToday(Long userId, LocalDate today);

	// 오늘 잡은 차례가 모두 실패했으면 다시 보낼 차례를 차지한다. 보내는 중인 차례가 있으면(잡은 수가 실패 수보다 많으면)
	// 잡지 않아, 메일이 늦게 가는 동안 다른 실행이 같은 메일을 또 보내지 않는다
	@Modifying
	@Query("update NotificationSetting s set s.sendAttemptCount = s.sendAttemptCount + 1 where s.userId = :userId"
		+ " and s.lastCheckedDate = :today and s.sendFailCount > 0 and s.sendAttemptCount = s.sendFailCount"
		+ " and s.sendAttemptCount < :maxAttempts and (s.lastSentDate is null or s.lastSentDate < :today)")
	int claimRetry(Long userId, LocalDate today, int maxAttempts);

	// 예약 알림 메일을 보낸 날을 적는다. 화면의 '마지막으로 보낸 날'이며, 보냈으니 앞서 보내지 못한 때는 지운다
	@Modifying
	@Query("update NotificationSetting s set s.lastSentDate = :today, s.lastFailedAt = null where s.userId = :userId")
	void markSent(Long userId, LocalDate today);

	// 오늘 잡은 차례에서 메일을 보내지 못했다고 적는다. 그사이 다음 날 차례가 잡혔으면 그 차례를 실패로 세지 않는다
	@Modifying
	@Query("update NotificationSetting s set s.sendFailCount = s.sendFailCount + 1, s.lastFailedAt = :failedAt"
		+ " where s.userId = :userId and s.lastCheckedDate = :today")
	void markFailed(Long userId, LocalDate today, LocalDateTime failedAt);

}
