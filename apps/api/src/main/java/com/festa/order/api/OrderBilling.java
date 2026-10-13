package com.festa.order.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o módulo de pagamento precisa do pedido (ARCHITECTURE.md §Pagamento). Valores sempre daqui, nunca do
 * navegador. As escritas entram na transação de quem chama (o processamento do webhook).
 */
public interface OrderBilling {

	/** Pedido para cobrar: total já inclui a taxa de serviço, que vai para a plataforma no split. */
	record BillableOrder(UUID orderId, UUID eventId, UUID organizationId, String eventName, String status,
			long totalCents, long feeCents, String buyerName, String buyerEmail, Instant expiresAt) {
	}

	enum PaidOutcome {
		/** Aguardava pagamento e virou pago. */
		CONFIRMED,
		/** Já estava pago (webhook repetido). */
		ALREADY_PAID,
		/** Tinha expirado, ainda havia estoque: reservou de novo e virou pago. */
		REVIVED,
		/** Tinha expirado e o estoque acabou: quem chama estorna e marca como estornado. */
		NO_STOCK
	}

	/** Pelo comprador: só com a chave do pedido (ADR-007). */
	Optional<BillableOrder> forBuyer(UUID orderId, String accessKey);

	Optional<BillableOrder> get(UUID orderId);

	/** Pagamento aprovado e conferido no gateway. Idempotente. */
	PaidOutcome paid(UUID orderId);

	/** Dinheiro devolvido (pagamento depois da expiração sem estoque, ou reembolso). */
	void refunded(UUID orderId);

}
