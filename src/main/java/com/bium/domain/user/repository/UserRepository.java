package com.bium.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmail(String email);

	// 이메일을 유일하게 만들기 전 DB에는 같은 이메일 계정이 여럿 있을 수 있어 목록으로 찾는다
	List<User> findAllByEmail(String email);

}
