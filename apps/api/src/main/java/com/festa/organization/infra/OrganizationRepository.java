package com.festa.organization.infra;

import com.festa.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	boolean existsBySlug(String slug);

}
