package com.festa.organization.app;

import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.organization.domain.Organization;
import com.festa.organization.domain.OrganizationMember;
import com.festa.organization.domain.Slug;
import com.festa.organization.infra.OrganizationMemberRepository;
import com.festa.organization.infra.OrganizationRepository;
import com.festa.shared.web.ApiException;
import com.festa.shared.storage.ImageUploads;
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
	private final ImageUploads uploads;

	OrganizationService(OrganizationRepository organizations, OrganizationMemberRepository members,
			TenantGuard tenantGuard, ImageUploads uploads) {
		this.organizations = organizations;
		this.members = members;
		this.tenantGuard = tenantGuard;
		this.uploads = uploads;
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

	/** Perfil público: só dono e admin (SECURITY.md). */
	@Transactional
	public Membership updateProfile(UUID organizationId, UUID userId, String name, String instagram, String whatsapp) {
		Role role = tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN);
		Organization organization = organizations.findById(organizationId).orElseThrow();
		try {
			organization.updateProfile(name, instagram, whatsapp);
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-profile", "Dados inválidos",
				ex.getMessage().startsWith("instagram") ? "Instagram inválido: use só o @ do perfil."
						: ex.getMessage().startsWith("whatsapp") ? "WhatsApp inválido: use DDD e número."
								: "Nome inválido.");
		}
		return new Membership(organization, role);
	}

	@Transactional(readOnly = true)
	public ImageUploads.UploadUrl logoUploadUrl(UUID organizationId, UUID userId, String contentType, long size) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN);
		return uploads.sign(logoFolder(organizationId), contentType, size);
	}

	/** Liga a logo já enviada; a chave precisa estar na pasta de logo desta organização. */
	@Transactional
	public Membership setLogo(UUID organizationId, UUID userId, String key) {
		Role role = tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN);
		Organization organization = organizations.findById(organizationId).orElseThrow();
		organization.changeLogo(key == null ? null : uploads.accept(logoFolder(organizationId), key).toString());
		return new Membership(organization, role);
	}

	private static String logoFolder(UUID organizationId) {
		return "orgs/%s/logo/".formatted(organizationId);
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
