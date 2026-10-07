package com.festa.notification.api;

/** Envio de e-mail transacional. Em dev vai para o Mailpit (http://localhost:8025). */
public interface EmailSender {

	void send(EmailMessage message);

}
