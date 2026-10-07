package com.festa.notification;

import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake de e-mail para testes: guarda as mensagens em vez de enviar. Use com {@code @Import(RecordingEmailSender.Config.class)}. */
public class RecordingEmailSender implements EmailSender {

	private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(EmailMessage message) {
		sent.add(message);
	}

	public List<EmailMessage> sentTo(String to) {
		return sent.stream().filter(message -> message.to().equals(to)).toList();
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
		}

	}

}
