package com.festa.organization.domain;

import com.festa.organization.api.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationTest {

	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

	private final Invitation invitation = new Invitation(UUID.randomUUID(), " Bia@Festa.com ", Role.PROMOTER,
		"hash", UUID.randomUUID(), NOW);

	@Test
	void normalizesEmailAndExpiresInSevenDays() {
		assertThat(invitation.getEmail()).isEqualTo("bia@festa.com");
		assertThat(invitation.getExpiresAt()).isEqualTo(NOW.plus(Invitation.VALIDITY));
	}

	@Test
	void ownerCannotBeInvited() {
		assertThatThrownBy(() -> new Invitation(UUID.randomUUID(), "bia@festa.com", Role.OWNER, "hash",
			UUID.randomUUID(), NOW)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void onlyTheInvitedEmailCanAcceptWhilePending() {
		assertThat(invitation.canBeAcceptedBy("bia@festa.com", NOW.plusSeconds(60))).isTrue();
		assertThat(invitation.canBeAcceptedBy("outra@festa.com", NOW.plusSeconds(60))).isFalse();
		assertThat(invitation.canBeAcceptedBy("bia@festa.com", invitation.getExpiresAt())).isFalse();
	}

	@Test
	void canBeAcceptedOnlyOnce() {
		invitation.accept(UUID.randomUUID(), "bia@festa.com", NOW);

		assertThat(invitation.isPending(NOW)).isFalse();
		assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), "bia@festa.com", NOW))
			.isInstanceOf(IllegalStateException.class);
	}

}
