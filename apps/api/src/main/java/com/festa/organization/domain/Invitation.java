package com.festa.organization.domain;

import com.festa.organization.api.Role;
import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Convite para entrar numa organização. Vale uma vez, por 7 dias, só para o e-mail convidado. */
@Entity
@Table(name = "organization_invitations")
public class Invitation extends BaseEntity {

	public static final Duration VALIDITY = Duration.ofDays(7);

	private UUID organizationId;

	private String email;

	@Enumerated(EnumType.STRING)
	private Role role;

	private String tokenHash;

	private UUID invitedByUserId;

	private Instant expiresAt;

	private Instant acceptedAt;

	private UUID acceptedByUserId;

	protected Invitation() {
	}

	public Invitation(UUID organizationId, String email, Role role, String tokenHash, UUID invitedByUserId,
			Instant now) {
		if (role == Role.OWNER) {
			throw new IllegalArgumentException("OWNER não é convidável");
		}
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.email = Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
		this.role = Objects.requireNonNull(role, "role");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
		this.invitedByUserId = Objects.requireNonNull(invitedByUserId, "invitedByUserId");
		this.expiresAt = now.plus(VALIDITY);
	}

	public boolean isPending(Instant now) {
		return acceptedAt == null && now.isBefore(expiresAt);
	}

	public boolean canBeAcceptedBy(String userEmail, Instant now) {
		return isPending(now) && email.equals(userEmail);
	}

	public void accept(UUID userId, String userEmail, Instant now) {
		if (!canBeAcceptedBy(userEmail, now)) {
			throw new IllegalStateException("convite não pode ser aceito");
		}
		acceptedAt = now;
		acceptedByUserId = Objects.requireNonNull(userId, "userId");
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
