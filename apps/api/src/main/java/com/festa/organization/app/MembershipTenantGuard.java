package com.festa.organization.app;

import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.organization.infra.OrganizationMemberRepository;
import com.festa.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

@Service
class MembershipTenantGuard implements TenantGuard {

	private final OrganizationMemberRepository members;

	MembershipTenantGuard(OrganizationMemberRepository members) {
		this.members = members;
	}

	@Override
	@Transactional(readOnly = true)
	public Role requireRole(UUID organizationId, UUID userId, Role... allowed) {
		Role role = members.findByOrganizationIdAndUserId(organizationId, userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Organização não encontrada."))
			.getRole();
		if (allowed.length > 0 && !Arrays.asList(allowed).contains(role)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado",
				"Seu papel nesta organização não permite esta ação.");
		}
		return role;
	}

}
