package com.festa.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MagicLinkTokenTest {

	@Test
	void generatesUrlSafe32ByteTokens() {
		String token = MagicLinkToken.generate();

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void generatesUniqueTokens() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			tokens.add(MagicLinkToken.generate());
		}

		assertThat(tokens).hasSize(1_000);
	}

	@Test
	void hashIsDeterministicSha256HexAndDiffersFromToken() {
		String token = MagicLinkToken.generate();

		String hash = MagicLinkToken.hash(token);

		assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(MagicLinkToken.hash(token)).isNotEqualTo(token);
	}

}
