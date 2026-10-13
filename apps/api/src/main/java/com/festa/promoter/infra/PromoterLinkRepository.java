package com.festa.promoter.infra;

import com.festa.promoter.domain.PromoterLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterLinkRepository extends JpaRepository<PromoterLink, UUID> {

	List<PromoterLink> findByEventIdAndOrganizationIdOrderByCreatedAt(UUID eventId, UUID organizationId);

	Optional<PromoterLink> findByIdAndEventIdAndOrganizationId(UUID id, UUID eventId, UUID organizationId);

	Optional<PromoterLink> findByEventIdAndCodeAndActiveTrue(UUID eventId, String code);

	boolean existsByEventIdAndCode(UUID eventId, String code);

	boolean existsByEventIdAndPromoterId(UUID eventId, UUID promoterId);

}
