package com.festa.organization.domain;

import com.festa.organization.api.Role;
import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "organization_members")
public class OrganizationMember extends BaseEntity {

	private UUID organizationId;

	private UUID userId;

	@Enumerated(EnumType.STRING)
	private Role role;

	protected OrganizationMember() {
	}

	public OrganizationMember(UUID organizationId, UUID userId, Role role) {
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.role = Objects.requireNonNull(role, "role");
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public Role getRole() {
		return role;
	}

}
