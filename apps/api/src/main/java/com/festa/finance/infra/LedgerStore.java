package com.festa.finance.infra;

import com.festa.shared.id.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Livro de lançamentos ({@code ledger_entries}). Só insere; estorno é lançamento negativo. */
@Repository
public class LedgerStore {

	private final JdbcClient jdbc;

	LedgerStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public enum EntryType {
		TICKET_SALE, SERVICE_FEE, TICKET_REFUND, SERVICE_FEE_REFUND
	}

	/** Idempotente por (pedido, tipo). Valor zero não vira lançamento (pedido sem taxa, por exemplo). */
	public void post(UUID organizationId, UUID eventId, UUID orderId, EntryType type, long amountCents, int tickets,
			String reason, Instant occurredAt) {
		if (amountCents == 0) {
			return;
		}
		jdbc.sql("""
				INSERT INTO ledger_entries (id, organization_id, event_id, order_id, entry_type, amount_cents, tickets,
				                            reason, occurred_at)
				VALUES (:id, :organizationId, :eventId, :orderId, :type, :amount, :tickets, :reason, :occurredAt)
				ON CONFLICT (order_id, entry_type) DO NOTHING
				""")
			.param("id", UuidV7.generate())
			.param("organizationId", organizationId)
			.param("eventId", eventId)
			.param("orderId", orderId)
			.param("type", type.name())
			.param("amount", amountCents)
			.param("tickets", tickets)
			.param("reason", reason)
			.param("occurredAt", Timestamp.from(occurredAt))
			.update();
	}

	/** Somas de um evento. Estornos vêm positivos (quanto saiu). */
	public record Totals(long ticketSalesCents, long ticketRefundsCents, int ticketsSold, int ticketsRefunded,
			int paidOrders, int refundedOrders, int chargebacks, long serviceFeeCents, long serviceFeeRefundsCents) {

		public static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0, 0, 0, 0);

		public Totals plus(Totals o) {
			return new Totals(ticketSalesCents + o.ticketSalesCents, ticketRefundsCents + o.ticketRefundsCents,
					ticketsSold + o.ticketsSold, ticketsRefunded + o.ticketsRefunded, paidOrders + o.paidOrders,
					refundedOrders + o.refundedOrders, chargebacks + o.chargebacks, serviceFeeCents + o.serviceFeeCents,
					serviceFeeRefundsCents + o.serviceFeeRefundsCents);
		}

	}

	public record EventTotals(UUID eventId, Totals totals) {
	}

	/** Por evento da organização; {@code eventId} nulo traz todos os eventos com lançamento. */
	public List<EventTotals> totals(UUID organizationId, UUID eventId) {
		return jdbc.sql("""
				SELECT event_id,
				       coalesce(sum(amount_cents) FILTER (WHERE entry_type = 'TICKET_SALE'), 0)          AS sales,
				       coalesce(-sum(amount_cents) FILTER (WHERE entry_type = 'TICKET_REFUND'), 0)       AS refunds,
				       coalesce(sum(tickets) FILTER (WHERE entry_type = 'TICKET_SALE'), 0)               AS tickets_sold,
				       coalesce(sum(tickets) FILTER (WHERE entry_type = 'TICKET_REFUND'), 0)             AS tickets_refunded,
				       count(*) FILTER (WHERE entry_type = 'TICKET_SALE')                                 AS paid_orders,
				       count(*) FILTER (WHERE entry_type = 'TICKET_REFUND' AND reason = 'REFUND')         AS refunded_orders,
				       count(*) FILTER (WHERE entry_type = 'TICKET_REFUND' AND reason = 'CHARGEBACK')     AS chargebacks,
				       coalesce(sum(amount_cents) FILTER (WHERE entry_type = 'SERVICE_FEE'), 0)           AS fees,
				       coalesce(-sum(amount_cents) FILTER (WHERE entry_type = 'SERVICE_FEE_REFUND'), 0)   AS fee_refunds
				  FROM ledger_entries
				 WHERE organization_id = :organizationId
				   AND (CAST(:eventId AS uuid) IS NULL OR event_id = CAST(:eventId AS uuid))
				 GROUP BY event_id
				""")
			.param("organizationId", organizationId)
			.param("eventId", eventId)
			.query((rs, i) -> new EventTotals(rs.getObject("event_id", UUID.class), totals(rs)))
			.list();
	}

	private static Totals totals(ResultSet rs) throws SQLException {
		return new Totals(rs.getLong("sales"), rs.getLong("refunds"), rs.getInt("tickets_sold"),
				rs.getInt("tickets_refunded"), rs.getInt("paid_orders"), rs.getInt("refunded_orders"),
				rs.getInt("chargebacks"), rs.getLong("fees"), rs.getLong("fee_refunds"));
	}

}
