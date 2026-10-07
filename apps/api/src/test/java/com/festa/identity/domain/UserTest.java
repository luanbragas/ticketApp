package com.festa.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

	@Test
	void normalizesEmail() {
		assertThat(User.normalizeEmail("  Ana@Festa.COM ")).isEqualTo("ana@festa.com");
	}

	@Test
	void accountFromMagicLinkIsVerifiedWithoutNameOrPassword() {
		User user = User.registerFromVerifiedEmail("Ana@Festa.com", NOW);

		assertThat(user.getEmail()).isEqualTo("ana@festa.com");
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getName()).isNull();
		assertThat(user.getPasswordHash()).isNull();
	}

	@Test
	void confirmingOwnershipOfUnverifiedAccountDiscardsPasswordSetByWhoeverCreatedIt() {
		User user = User.registerWithPassword("Ana", "ana@festa.com", "$argon2id$hash-de-terceiro");

		boolean credentialsReset = user.confirmEmailOwnership(NOW);

		assertThat(credentialsReset).isTrue();
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getPasswordHash()).isNull();
	}

	@Test
	void confirmingOwnershipOfVerifiedAccountChangesNothing() {
		User user = User.registerFromVerifiedEmail("ana@festa.com", NOW);

		boolean credentialsReset = user.confirmEmailOwnership(NOW.plusSeconds(60));

		assertThat(credentialsReset).isFalse();
		assertThat(user.isEmailVerified()).isTrue();
	}

}
