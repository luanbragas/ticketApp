package com.festa.payment.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Estornos ({@code refunds}): um por pagamento, nunca apagado. */
@Repository
public class RefundStore {

	private final JdbcClient jdbc;

	RefundStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public enum Kind {
		REFUND, CHARGEBACK
	}

	/** Idempotente: o mesmo pagamento estornado de novo (aviso repetido) não duplica. */
	public void record(UUID id, UUID paymentId, UUID orderId, UUID organizationId, Kind kind, long amountCents,
			String reason, UUID createdBy) {
		jdbc.sql("""
				INSERT INTO refunds (id, payment_id, order_id, organization_id, kind, amount_cents, reason, created_by)
				VALUES (:id, :paymentId, :orderId, :organizationId, :kind, :amount, :reason, :createdBy)
				ON CONFLICT (payment_id) DO NOTHING
				""")
			.param("id", id)
			.param("paymentId", paymentId)
			.param("orderId", orderId)
			.param("organizationId", organizationId)
			.param("kind", kind.name())
			.param("amount", amountCents)
			.param("reason", reason)
			.param("createdBy", createdBy)
			.update();
	}

}
