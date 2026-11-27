package com.bium.domain.notification.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
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

	// 알림 메일을 받을 주소. 로그인 이메일과 따로 두어 알림을 끄고 지워도 로그인에는 영향이 없다
	@Column(length = 255)
	private String email;

	private boolean enabled = true;

	private LocalTime notifyTime;

	private boolean notifyD3 = true;

	private boolean notifyDday = true;

	// 그날 보낼 차례를 잡은 날. 같은 날 두 번 보내지 않게 하며, 알릴 재료가 없거나 보내지 못한 날도 적는다
	private LocalDate lastCheckedDate;

	// 예약 알림 메일을 실제로 보낸 마지막 날
	private LocalDate lastSentDate;

	public NotificationSetting(User user, LocalTime notifyTime) {
		this.user = user;
		this.notifyTime = notifyTime;
	}

	public void changeEmail(String email) {
		this.email = email;
	}

	public void update(boolean enabled, LocalTime notifyTime, boolean notifyD3, boolean notifyDday) {
		this.enabled = enabled;
		this.notifyTime = notifyTime;
		this.notifyD3 = notifyD3;
		this.notifyDday = notifyDday;
	}

}
