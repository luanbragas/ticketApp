package com.festa.ticketing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Token do QR: opaco, sem dado pessoal, remontável só com a chave (ADR-008). */
class TicketTokensTest {

	private final TicketTokens tokens = new TicketTokens(new byte[32]);

	@Test
	void sameNonceSameTokenAndNewNonceNewToken() {
		byte[] nonce = TicketTokens.newNonce();

		String token = tokens.tokenFor(nonce);

		assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
		assertThat(tokens.tokenFor(nonce)).isEqualTo(token);
		assertThat(tokens.tokenFor(TicketTokens.newNonce())).isNotEqualTo(token);
		assertThat(TicketTokens.looksValid(token)).isTrue();
	}

	@Test
	void withoutTheKeyTheNonceDoesNotGiveTheToken() {
		byte[] nonce = TicketTokens.newNonce();
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;

		assertThat(new TicketTokens(otherKey).tokenFor(nonce)).isNotEqualTo(tokens.tokenFor(nonce));
	}

	@Test
	void hashIsWhatTheDatabaseKeeps() {
		String token = tokens.tokenFor(TicketTokens.newNonce());

		assertThat(TicketTokens.hash(token)).hasSize(64).isNotEqualTo(token);
	}

	@Test
	void rejectsJunkAndShortKeys() {
		assertThat(TicketTokens.looksValid("../../etc")).isFalse();
		assertThat(TicketTokens.looksValid(null)).isFalse();
		assertThatThrownBy(() -> new TicketTokens(new byte[16])).isInstanceOf(IllegalArgumentException.class);
	}

}
