package com.festa.organization.infra;

import com.festa.organization.domain.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

	Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

	List<OrganizationMember> findByUserId(UUID userId);

}
