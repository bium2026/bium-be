package com.bium.domain.notification.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * JavaMailSender로 글자 메일을 보낸다. SMTP 설정(MAIL_HOST 등)이 없으면 보내지 않고 실패로 알린다.
 */
@Slf4j
@Component
public class ExpiryMailSender {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String from;

	public ExpiryMailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${notification.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
		if (!StringUtils.hasText(from)) {
			log.warn("MAIL_FROM·MAIL_USERNAME이 설정되지 않았습니다. 설정할 때까지 유통기한 알림 메일은 실패로 기록됩니다.");
		}
	}

	// 실패하면 MailException을 던진다. 호출한 쪽이 실패 기록을 남긴다
	public void send(String to, String subject, String text) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null || !StringUtils.hasText(from)) {
			throw new MailSendException("메일 서버 설정이 없습니다.");
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		sender.send(message);
	}

	static class MailSendException extends MailException {

		MailSendException(String message) {
			super(message);
		}

	}

}
