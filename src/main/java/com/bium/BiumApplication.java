package com.bium;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BiumApplication {

	public static void main(String[] args) {
		// @CreationTimestamp 같은 LocalDateTime 기록은 JVM 시간대를 따른다.
		// 서버가 UTC로 돌아도 활동 기록·참여일이 한국 시각으로 남도록 Clock 빈(ClockConfig)과 같은 시간대로 맞춘다
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(BiumApplication.class, args);
	}

}
