package com.festa.ticketing.app;

import com.festa.ticketing.api.Inventory;
import com.festa.ticketing.domain.TicketBatch;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** Estoque por SQL atômico. A virada (ADR-006) não roda aqui: reserva não muda status de lote. */
@Service
class JdbcInventory implements Inventory {

	private final JdbcClient jdbc;
	private final Clock clock;
	private final TicketingService ticketing;

	JdbcInventory(JdbcClient jdbc, Clock clock, TicketingService ticketing) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.ticketing = ticketing;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Offer> offers(UUID eventId, Collection<UUID> batchIds) {
		if (batchIds.isEmpty()) {
			return List.of();
		}
		Instant now = clock.instant();
		return jdbc.sql("""
				SELECT b.id, b.event_id, b.name AS batch_name, t.name AS type_name, t.is_half_price, b.price_cents,
				       b.max_per_order, b.status, b.sales_start_at, b.sales_end_at, b.capacity - b.sold - b.reserved AS remaining
				  FROM ticket_batches b JOIN ticket_types t ON t.id = b.ticket_type_id
				 WHERE b.event_id = :eventId AND b.id IN (:ids)
				""")
			.param("eventId", eventId)
			.param("ids", batchIds)
			.query((rs, i) -> {
				Timestamp start = rs.getTimestamp("sales_start_at");
				Timestamp end = rs.getTimestamp("sales_end_at");
				boolean inWindow = (start == null || !start.toInstant().isAfter(now))
						&& (end == null || end.toInstant().isAfter(now));
				int max = rs.getObject("max_per_order") == null ? TicketBatch.DEFAULT_MAX_PER_ORDER : rs.getInt("max_per_order");
				return new Offer(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
						rs.getString("batch_name"), rs.getString("type_name"), rs.getBoolean("is_half_price"),
						rs.getLong("price_cents"), max, "ON_SALE".equals(rs.getString("status")) && inWindow,
						rs.getInt("remaining"));
			})
			.list();
	}

	/**
	 * O {@code UPDATE} só passa se ainda couber: com 500 pedidos ao mesmo tempo num lote de 100, o Postgres
	 * enfileira as linhas e exatamente 100 passam. A janela de venda também é conferida aqui, para um lote
	 * cuja virada passou não vender até o job rodar.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public OptionalLong reserve(UUID eventId, UUID batchId, int quantity, Instant now) {
		if (quantity < 1) {
			throw new IllegalArgumentException("quantidade inválida: " + quantity);
		}
		return jdbc.sql("""
				UPDATE ticket_batches
				   SET reserved = reserved + :qty, updated_at = now()
				 WHERE id = :id
				   AND event_id = :eventId
				   AND status = 'ON_SALE'
				   AND (sales_start_at IS NULL OR sales_start_at <= :now)
				   AND (sales_end_at IS NULL OR sales_end_at > :now)
				   AND sold + reserved + :qty <= capacity
				RETURNING price_cents
				""")
			.param("qty", quantity)
			.param("id", batchId)
			.param("eventId", eventId)
			.param("now", Timestamp.from(now))
			.query(Long.class)
			.optional()
			.map(OptionalLong::of)
			.orElse(OptionalLong.empty());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void release(UUID batchId, int quantity) {
		int updated = jdbc.sql("""
				UPDATE ticket_batches SET reserved = reserved - :qty, updated_at = now()
				 WHERE id = :id AND reserved >= :qty
				""")
			.param("qty", quantity)
			.param("id", batchId)
			.update();
		if (updated != 1) {
			throw new IllegalStateException("reserva do lote " + batchId + " menor que " + quantity);
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void confirm(UUID eventId, UUID batchId, int quantity) {
		int updated = jdbc.sql("""
				UPDATE ticket_batches SET reserved = reserved - :qty, sold = sold + :qty, updated_at = now()
				 WHERE id = :id AND event_id = :eventId AND reserved >= :qty
				""")
			.param("qty", quantity)
			.param("id", batchId)
			.param("eventId", eventId)
			.update();
		if (updated != 1) {
			throw new IllegalStateException("reserva do lote " + batchId + " menor que " + quantity);
		}
		UUID organizationId = jdbc.sql("SELECT organization_id FROM ticket_batches WHERE id = :id")
			.param("id", batchId)
			.query(UUID.class)
			.single();
		ticketing.rolloverEvent(organizationId, eventId);
	}

	@Override
	@Transactional(readOnly = true)
	public Stock stock(UUID eventId) {
		return jdbc.sql("""
				SELECT coalesce(sum(CASE WHEN status IN ('SOLD_OUT', 'CLOSED') THEN sold + reserved ELSE capacity END), 0) AS offered,
				       coalesce(sum(sold), 0) AS sold, coalesce(sum(reserved), 0) AS reserved
				  FROM ticket_batches WHERE event_id = :eventId
				""")
			.param("eventId", eventId)
			.query((rs, i) -> new Stock(rs.getInt("offered"), rs.getInt("sold"), rs.getInt("reserved")))
			.single();
	}

}
