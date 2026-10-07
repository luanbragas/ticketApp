package com.festa.organization.domain;

import com.festa.organization.api.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.festa.organization.api.Role.ADMIN;
import static com.festa.organization.api.Role.CHECKIN_OPERATOR;
import static com.festa.organization.api.Role.MANAGER;
import static com.festa.organization.api.Role.OWNER;
import static com.festa.organization.api.Role.PROMOTER;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationPolicyTest {

	@Test
	void ownerInvitesAnyRoleButOwner() {
		assertThat(InvitationPolicy.canInvite(OWNER, ADMIN)).isTrue();
		assertThat(InvitationPolicy.canInvite(OWNER, MANAGER)).isTrue();
		assertThat(InvitationPolicy.canInvite(OWNER, PROMOTER)).isTrue();
		assertThat(InvitationPolicy.canInvite(OWNER, CHECKIN_OPERATOR)).isTrue();
		assertThat(InvitationPolicy.canInvite(OWNER, OWNER)).isFalse();
	}

	@Test
	void adminInvitesStaffButNotOtherAdmins() {
		assertThat(InvitationPolicy.canInvite(ADMIN, MANAGER)).isTrue();
		assertThat(InvitationPolicy.canInvite(ADMIN, PROMOTER)).isTrue();
		assertThat(InvitationPolicy.canInvite(ADMIN, CHECKIN_OPERATOR)).isTrue();
		assertThat(InvitationPolicy.canInvite(ADMIN, ADMIN)).isFalse();
		assertThat(InvitationPolicy.canInvite(ADMIN, OWNER)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "MANAGER", "PROMOTER", "CHECKIN_OPERATOR" })
	void otherRolesDoNotInvite(Role inviter) {
		assertThat(InvitationPolicy.canInvite(inviter)).isFalse();
		for (Role invited : Role.values()) {
			assertThat(InvitationPolicy.canInvite(inviter, invited)).isFalse();
		}
	}

}
