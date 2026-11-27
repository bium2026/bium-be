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

	// 로그인에 쓰는 이메일. 아이디로 로그인하던 때 이메일 없이 만든 계정은 비어 있을 수 있다
	@Column(unique = true, length = 255)
	private String email;

	// BCrypt로 암호화한 값
	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 10)
	private String nickname;

	public User(String email, String password, String nickname) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

}
