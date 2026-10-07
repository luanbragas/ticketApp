package com.festa.notification.api;

import java.util.Objects;

/** E-mail em texto puro. */
public record EmailMessage(String to, String subject, String text) {

	public EmailMessage {
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(subject, "subject");
		Objects.requireNonNull(text, "text");
	}

}
