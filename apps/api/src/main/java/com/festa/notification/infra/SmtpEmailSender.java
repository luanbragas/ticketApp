package com.festa.notification.infra;

import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	SmtpEmailSender(JavaMailSender mailSender, @Value("${festa.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(EmailMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(from);
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.text());
		mailSender.send(mail);
	}

}
