package com.festa.ticketing.infra;

import com.festa.ticketing.domain.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

	/** Sempre com evento e organização: tipo de outro evento é tratado como inexistente. */
	Optional<TicketType> findByIdAndEventIdAndOrganizationId(UUID id, UUID eventId, UUID organizationId);

	List<TicketType> findByEventIdAndOrganizationIdOrderByPosition(UUID eventId, UUID organizationId);

	@Query("SELECT COALESCE(MAX(t.position) + 1, 0) FROM TicketType t WHERE t.eventId = :eventId")
	int nextPosition(UUID eventId);

}
