package com.festa.order.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas de pedido que precisam de SQL do Postgres (trava consultiva, SKIP LOCKED). */
@Repository
public class OrderQueries {

	private final JdbcClient jdbc;

	OrderQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Fila única por comprador no evento até o fim da transação: dois pedidos do mesmo CPF ao mesmo tempo
	 * não passam juntos do limite por CPF.
	 */
	public void lockBuyer(UUID eventId, String buyerCpfHash) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
			.param("key", "order-cpf:" + eventId + ":" + buyerCpfHash)
			.query()
			.singleRow();
	}

	/** Ingressos que o CPF já tem no evento: pedidos pagos e os que ainda seguram reserva. */
	public int ticketsHeldBy(UUID eventId, String buyerCpfHash) {
		return jdbc.sql("""
				SELECT count(*) FROM order_items i JOIN orders o ON o.id = i.order_id
				 WHERE o.event_id = :eventId AND o.buyer_cpf_hash = :hash
				   AND o.status IN ('PENDING_PAYMENT', 'PAID')
				""")
			.param("eventId", eventId)
			.param("hash", buyerCpfHash)
			.query(Integer.class)
			.single();
	}

	/**
	 * Pedidos vencidos travados para expirar. {@code SKIP LOCKED}: duas instâncias do job (ou o webhook do
	 * M5 mexendo no mesmo pedido) não brigam pela mesma linha.
	 */
	public List<UUID> lockExpired(Instant now, int limit) {
		return jdbc.sql("""
				SELECT id FROM orders
				 WHERE status = 'PENDING_PAYMENT' AND expires_at <= :now
				 ORDER BY expires_at
				 LIMIT :limit
				 FOR UPDATE SKIP LOCKED
				""")
			.param("now", Timestamp.from(now))
			.param("limit", limit)
			.query(UUID.class)
			.list();
	}

}
