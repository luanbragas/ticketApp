package com.festa.organization.web;

import com.festa.organization.api.Role;
import com.festa.organization.app.OrganizationService;
import com.festa.organization.app.OrganizationService.Membership;
import com.festa.organization.domain.Organization;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs")
class OrganizationController {

	private final OrganizationService organizations;

	OrganizationController(OrganizationService organizations) {
		this.organizations = organizations;
	}

	record CreateOrganizationRequest(
			@NotBlank(message = "Informe o nome da organização.") @Size(max = 80, message = "Nome muito longo.") String name,
			@Size(min = 3, max = 60, message = "O endereço deve ter entre 3 e 60 caracteres.")
			@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Use só letras minúsculas, números e hífens.") String slug) {
	}

	record OrganizationResponse(UUID id, String name, String slug, String logoUrl, String instagram, String whatsapp,
			Role role) {

		static OrganizationResponse of(Membership membership) {
			Organization organization = membership.organization();
			return new OrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(),
				organization.getLogoUrl(), organization.getInstagram(), organization.getWhatsapp(), membership.role());
		}

	}

	record OrganizationList(List<OrganizationResponse> items) {
	}

	@PostMapping
	ResponseEntity<OrganizationResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreateOrganizationRequest body) {
		Membership membership = organizations.create(user.id(), body.name(), body.slug());
		return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.of(membership));
	}

	/** Organizações do usuário logado (seletor do painel). */
	@GetMapping
	OrganizationList mine(@AuthenticationPrincipal AuthenticatedUser user) {
		return new OrganizationList(organizations.listForUser(user.id()).stream().map(OrganizationResponse::of).toList());
	}

	@GetMapping("/{orgId}")
	OrganizationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		return OrganizationResponse.of(organizations.get(orgId, user.id()));
	}

}
