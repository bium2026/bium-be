package com.bium.domain.notification.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bium.domain.notification.entity.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	// 알림을 켰고 받을 이메일이 있으며, 알림 시각이 지났는데 오늘 아직 보내지 않은 사용자
	@Query("select s from NotificationSetting s join fetch s.user where s.enabled = true and s.email is not null"
		+ " and s.notifyTime <= :now and (s.lastSentDate is null or s.lastSentDate < :today)")
	List<NotificationSetting> findDue(LocalTime now, LocalDate today);

	// 오늘 보낼 차례를 먼저 차지한다. 예약 작업이 겹치거나 서버가 여러 대여도 한 요청만 1을 받아 같은 날 두 번 보내지 않는다
	@Modifying
	@Query("update NotificationSetting s set s.lastSentDate = :today where s.userId = :userId"
		+ " and (s.lastSentDate is null or s.lastSentDate < :today)")
	int claimToday(Long userId, LocalDate today);

}
