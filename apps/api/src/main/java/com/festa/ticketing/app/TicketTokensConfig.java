package com.festa.ticketing.app;

import com.festa.ticketing.domain.TicketTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
class TicketTokensConfig {

	/** Chave do QR em TICKET_TOKEN_KEY (32 bytes em base64). Trocar invalida todos os ingressos emitidos. */
	@Bean
	TicketTokens ticketTokens(@Value("${festa.crypto.ticket-token-key}") String key) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(key.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("festa.crypto.ticket-token-key precisa ser base64", ex);
		}
		return new TicketTokens(bytes);
	}

}
