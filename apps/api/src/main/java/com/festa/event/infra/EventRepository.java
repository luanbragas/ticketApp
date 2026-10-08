package com.festa.event.infra;

import com.festa.event.domain.Event;
import com.festa.event.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	/** Sempre com a organização: evento de outra organização é tratado como inexistente. */
	Optional<Event> findByIdAndOrganizationId(UUID id, UUID organizationId);

	boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);

	boolean existsBySlug(String slug);

	/** Próximos primeiro; sem data (rascunho) no fim, por criação. */
	@Query("""
			SELECT e FROM Event e WHERE e.organizationId = :organizationId
			  AND (:status IS NULL OR e.status = :status)
			ORDER BY e.startsAt ASC NULLS LAST, e.createdAt DESC""")
	List<Event> findForOrganization(UUID organizationId, EventStatus status);

}
