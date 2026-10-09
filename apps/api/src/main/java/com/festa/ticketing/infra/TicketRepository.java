package com.festa.ticketing.infra;

import com.festa.ticketing.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	Optional<Ticket> findByTokenHash(String tokenHash);

	List<Ticket> findByOrderIdOrderByCreatedAt(UUID orderId);

	List<Ticket> findByBuyerEmailOrderByCreatedAt(String buyerEmail);

	/** Itens que já viraram ingresso: a emissão repetida pula esses (outbox entrega "pelo menos uma vez"). */
	List<Ticket> findByOrderItemIdIn(Collection<UUID> orderItemIds);

}
