package com.bium.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String loginId;

	// BCrypt로 암호화한 값
	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 10)
	private String nickname;

	// 유통기한 알림 메일을 받을 주소. 가입할 때 받고 알림 설정에서 바꿀 수 있다.
	// 이메일 없이 가입한 기존 계정이 있고 알림을 끄면 지울 수도 있어 비워 둘 수 있다
	@Column(length = 255)
	private String email;

	public User(String loginId, String password, String nickname) {
		this.loginId = loginId;
		this.password = password;
		this.nickname = nickname;
	}

	public void changeEmail(String email) {
		this.email = email;
	}

}
