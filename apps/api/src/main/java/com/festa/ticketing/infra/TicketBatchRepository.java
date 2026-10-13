package com.festa.ticketing.infra;

import com.festa.ticketing.domain.BatchStatus;
import com.festa.ticketing.domain.TicketBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketBatchRepository extends JpaRepository<TicketBatch, UUID> {

	List<TicketBatch> findByEventIdAndOrganizationIdOrderByPosition(UUID eventId, UUID organizationId);

	/**
	 * Lotes do evento travados para escrita: edição, virada e (no M4) reserva do mesmo evento acontecem
	 * uma de cada vez, sem decisão em cima de foto velha.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT b FROM TicketBatch b WHERE b.eventId = :eventId AND b.organizationId = :organizationId ORDER BY b.ticketTypeId, b.position")
	List<TicketBatch> lockForEvent(UUID eventId, UUID organizationId);

	@Query("SELECT COALESCE(MAX(b.position) + 1, 0) FROM TicketBatch b WHERE b.ticketTypeId = :ticketTypeId")
	int nextPosition(UUID ticketTypeId);

	boolean existsByEventIdAndStatusIn(UUID eventId, List<BatchStatus> statuses);

	/**
	 * Eventos com lote que pode virar agora: à venda que esgotou ou passou da data, ou agendado que já
	 * pode abrir ou fechar num tipo sem lote à venda.
	 */
	@Query(value = """
			SELECT DISTINCT b.event_id, b.organization_id FROM ticket_batches b
			 WHERE (b.status = 'ON_SALE' AND (b.sold >= b.capacity OR b.sales_end_at <= :now))
			    OR (b.status = 'SCHEDULED'
			        AND (b.sales_start_at IS NULL OR b.sales_start_at <= :now OR b.sales_end_at <= :now)
			        AND NOT EXISTS (SELECT 1 FROM ticket_batches o
			                         WHERE o.ticket_type_id = b.ticket_type_id AND o.status = 'ON_SALE'))
			""", nativeQuery = true)
	List<Object[]> findEventsDueForRollover(Instant now);

}
