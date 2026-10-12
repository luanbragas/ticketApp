package com.festa.order.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio publicado no outbox quando um pedido pago devolve o dinheiro (reembolso ou chargeback).
 * Ingressos são cancelados (ticketing), a venda sai da conta do promoter e o comprador recebe um e-mail.
 */
public record OrderRefunded(UUID orderId, UUID eventId, UUID organizationId, String buyerName, String buyerEmail,
		UUID promoterId, long subtotalCents, long feeCents, long totalCents, int tickets, String kind, Instant refundedAt) {

	public static final String TYPE = "OrderRefunded";

	public static final String REFUND = "REFUND";

	public static final String CHARGEBACK = "CHARGEBACK";

}
