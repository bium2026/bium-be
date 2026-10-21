package com.bium.domain.notification.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bium.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

	private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@CreationTimestamp
	private LocalDateTime sentAt;

	private Integer itemCount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private NotificationStatus status;

	@Column(length = ERROR_MESSAGE_MAX_LENGTH)
	private String errorMessage;

	public NotificationLog(User user, Integer itemCount, NotificationStatus status, String errorMessage) {
		this.user = user;
		this.itemCount = itemCount;
		this.status = status;
		// 메일 예외 메시지는 SMTP 응답 등으로 길어질 수 있고, 컬럼 길이를 넘으면 실패 기록 자체가 저장되지 않는다
		this.errorMessage = (errorMessage != null && errorMessage.length() > ERROR_MESSAGE_MAX_LENGTH)
			? errorMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH)
			: errorMessage;
	}

}
