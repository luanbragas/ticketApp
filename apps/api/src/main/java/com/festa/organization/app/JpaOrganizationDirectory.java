package com.festa.organization.app;

import com.festa.organization.api.OrganizationDirectory;
import com.festa.organization.infra.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class JpaOrganizationDirectory implements OrganizationDirectory {

	private final OrganizationRepository organizations;

	JpaOrganizationDirectory(OrganizationRepository organizations) {
		this.organizations = organizations;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OrganizationSummary> find(UUID organizationId) {
		return organizations.findById(organizationId)
			.map(o -> new OrganizationSummary(o.getId(), o.getName(), o.getSlug(), o.getLogoUrl(), o.getInstagram()));
	}

}
