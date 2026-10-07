package com.festa.organization.domain;

import com.festa.organization.api.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quem pode convidar e com qual papel:
 * <ul>
 * <li>OWNER convida ADMIN, MANAGER, PROMOTER e CHECKIN_OPERATOR;</li>
 * <li>ADMIN convida MANAGER, PROMOTER e CHECKIN_OPERATOR (não cria outros ADMINs);</li>
 * <li>demais papéis não convidam;</li>
 * <li>ninguém é convidado como OWNER: cada organização tem um único dono.</li>
 * </ul>
 */
public final class InvitationPolicy {

	private static final Set<Role> STAFF = EnumSet.of(Role.MANAGER, Role.PROMOTER, Role.CHECKIN_OPERATOR);

	private InvitationPolicy() {
	}

	public static boolean canInvite(Role inviter) {
		return inviter == Role.OWNER || inviter == Role.ADMIN;
	}

	public static boolean canInvite(Role inviter, Role invited) {
		return switch (inviter) {
			case OWNER -> invited != Role.OWNER;
			case ADMIN -> STAFF.contains(invited);
			default -> false;
		};
	}

}
