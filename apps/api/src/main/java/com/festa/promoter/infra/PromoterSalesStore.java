package com.festa.promoter.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Vendas pagas por promoter ({@code promoter_sales}), gravadas a partir do evento {@code OrderPaid}. */
@Repository
public class PromoterSalesStore {

	private final JdbcClient jdbc;

	PromoterSalesStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record Totals(int tickets, long revenueCents) {

		public static final Totals ZERO = new Totals(0, 0);

	}

	/** Idempotente: o mesmo pedido entregue duas vezes conta uma vez só. */
	public void record(UUID orderId, UUID promoterId, UUID eventId, UUID organizationId, int tickets,
			long revenueCents, Instant paidAt) {
		jdbc.sql("""
				INSERT INTO promoter_sales (order_id, promoter_id, event_id, organization_id, tickets, revenue_cents, paid_at)
				VALUES (:orderId, :promoterId, :eventId, :organizationId, :tickets, :revenue, :paidAt)
				ON CONFLICT (order_id) DO NOTHING
				""")
			.param("orderId", orderId)
			.param("promoterId", promoterId)
			.param("eventId", eventId)
			.param("organizationId", organizationId)
			.param("tickets", tickets)
			.param("revenue", revenueCents)
			.param("paidAt", Timestamp.from(paidAt))
			.update();
	}

	/** Totais por promoter num evento da organização. */
	public Map<UUID, Totals> byPromoter(UUID eventId, UUID organizationId) {
		return jdbc.sql("""
				SELECT promoter_id, sum(tickets) AS tickets, sum(revenue_cents) AS revenue
				  FROM promoter_sales
				 WHERE event_id = :eventId AND organization_id = :organizationId
				 GROUP BY promoter_id
				""")
			.param("eventId", eventId)
			.param("organizationId", organizationId)
			.query((rs, i) -> Map.entry(rs.getObject("promoter_id", UUID.class),
					new Totals(rs.getInt("tickets"), rs.getLong("revenue"))))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
