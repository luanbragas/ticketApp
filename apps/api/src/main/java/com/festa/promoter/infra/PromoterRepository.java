package com.festa.promoter.infra;

import com.festa.promoter.domain.Promoter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterRepository extends JpaRepository<Promoter, UUID> {

	Optional<Promoter> findByIdAndOrganizationId(UUID id, UUID organizationId);

	Optional<Promoter> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

	List<Promoter> findByOrganizationIdOrderByName(UUID organizationId);

}
