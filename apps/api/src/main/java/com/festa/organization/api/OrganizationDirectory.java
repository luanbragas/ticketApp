package com.festa.organization.api;

import java.util.Optional;
import java.util.UUID;

/** Leitura de dados públicos da organização para outros módulos (que não podem ler a tabela organizations). */
public interface OrganizationDirectory {

	Optional<OrganizationSummary> find(UUID organizationId);

	record OrganizationSummary(UUID id, String name, String slug, String logoUrl, String instagram) {
	}

}
