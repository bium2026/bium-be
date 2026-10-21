package com.bium.domain.notification.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.bium.domain.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 유통기한 알림 설정. 알림은 이메일로 보낸다
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

	@Id
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	private boolean enabled = true;

	private LocalTime notifyTime;

	private boolean notifyD3 = true;

	private boolean notifyDday = true;

	// 같은 날 중복 발송 방지
	private LocalDate lastSentDate;

	public NotificationSetting(User user, LocalTime notifyTime) {
		this.user = user;
		this.notifyTime = notifyTime;
	}

}
