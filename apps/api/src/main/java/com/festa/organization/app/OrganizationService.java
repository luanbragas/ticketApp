package com.festa.organization.app;

import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.organization.domain.Organization;
import com.festa.organization.domain.OrganizationMember;
import com.festa.organization.domain.Slug;
import com.festa.organization.infra.OrganizationMemberRepository;
import com.festa.organization.infra.OrganizationRepository;
import com.festa.shared.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

	private static final int MAX_SLUG_SUFFIX = 50;

	private final OrganizationRepository organizations;
	private final OrganizationMemberRepository members;
	private final TenantGuard tenantGuard;

	OrganizationService(OrganizationRepository organizations, OrganizationMemberRepository members,
			TenantGuard tenantGuard) {
		this.organizations = organizations;
		this.members = members;
		this.tenantGuard = tenantGuard;
	}

	public record Membership(Organization organization, Role role) {
	}

	/** Cria a organização e torna quem criou o OWNER. Sem slug informado, gera um a partir do nome. */
	@Transactional
	public Membership create(UUID userId, String name, String requestedSlug) {
		String slug;
		if (requestedSlug != null) {
			if (organizations.existsBySlug(requestedSlug)) {
				throw slugTaken();
			}
			slug = requestedSlug;
		}
		else {
			slug = availableSlugFor(name);
		}
		Organization organization;
		try {
			organization = organizations.saveAndFlush(new Organization(name, slug));
		}
		catch (DataIntegrityViolationException ex) {
			// Outra organização pegou o mesmo slug entre a checagem e o INSERT.
			throw slugTaken();
		}
		members.save(new OrganizationMember(organization.getId(), userId, Role.OWNER));
		return new Membership(organization, Role.OWNER);
	}

	/** Organizações em que o usuário é membro, por nome. */
	@Transactional(readOnly = true)
	public List<Membership> listForUser(UUID userId) {
		Map<UUID, Role> roles = members.findByUserId(userId).stream()
			.collect(Collectors.toMap(OrganizationMember::getOrganizationId, OrganizationMember::getRole));
		return organizations.findAllById(roles.keySet()).stream()
			.map(organization -> new Membership(organization, roles.get(organization.getId())))
			.sorted(Comparator.comparing(membership -> membership.organization().getName(),
				String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	@Transactional(readOnly = true)
	public Membership get(UUID organizationId, UUID userId) {
		Role role = tenantGuard.requireRole(organizationId, userId);
		return organizations.findById(organizationId)
			.map(organization -> new Membership(organization, role))
			.orElseThrow(() -> new IllegalStateException("membro de organização inexistente: " + organizationId));
	}

	private String availableSlugFor(String name) {
		String base = Slug.fromName(name);
		if (base.length() < Slug.MIN_LENGTH) {
			base = base.isEmpty() ? "org" : base + "-org";
		}
		if (!organizations.existsBySlug(base)) {
			return base;
		}
		for (int suffix = 2; suffix <= MAX_SLUG_SUFFIX; suffix++) {
			String candidate = Slug.withSuffix(base, suffix);
			if (!organizations.existsBySlug(candidate)) {
				return candidate;
			}
		}
		throw slugTaken();
	}

	private static ApiException slugTaken() {
		return new ApiException(HttpStatus.CONFLICT, "slug-taken", "Endereço indisponível",
			"Já existe uma organização com este endereço. Escolha outro.");
	}

}
