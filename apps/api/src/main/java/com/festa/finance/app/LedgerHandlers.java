package com.festa.finance.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.festa.finance.infra.LedgerStore;
import com.festa.finance.infra.LedgerStore.EntryType;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lançamentos a partir dos eventos de pedido (ARCHITECTURE.md: módulos conversam por evento). Cada handler lê o
 * JSON com um contrato próprio, só com o que precisa: o finance não depende do módulo de pedidos.
 */
final class LedgerHandlers {

	private LedgerHandlers() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PaidOrder(UUID orderId, UUID eventId, UUID organizationId, long subtotalCents, Long feeCents,
			Long totalCents, Instant paidAt, List<Object> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RefundedOrder(UUID orderId, UUID eventId, UUID organizationId, long feeCents, long totalCents,
			int tickets, String kind, Instant refundedAt) {
	}

	/** {@code OrderPaid}: valor dos ingressos (do produtor) e taxa de serviço (da plataforma). */
	@Component
	static class Paid implements OutboxHandler {

		private final LedgerStore ledger;
		private final JsonMapper json;

		Paid(LedgerStore ledger, JsonMapper json) {
			this.ledger = ledger;
			this.json = json;
		}

		@Override
		public String type() {
			return "OrderPaid";
		}

		@Override
		public void handle(OutboxMessage message) {
			PaidOrder paid = json.readValue(message.payload(), PaidOrder.class);
			long fee = paid.feeCents() == null ? 0 : paid.feeCents();
			long tickets = paid.totalCents() == null ? paid.subtotalCents() : paid.totalCents() - fee;
			ledger.post(paid.organizationId(), paid.eventId(), paid.orderId(), EntryType.TICKET_SALE, tickets,
					paid.items().size(), null, paid.paidAt());
			ledger.post(paid.organizationId(), paid.eventId(), paid.orderId(), EntryType.SERVICE_FEE, fee, 0, null,
					paid.paidAt());
		}

	}

	/** {@code OrderRefunded}: os mesmos valores, negativos. */
	@Component
	static class Refunded implements OutboxHandler {

		private final LedgerStore ledger;
		private final JsonMapper json;

		Refunded(LedgerStore ledger, JsonMapper json) {
			this.ledger = ledger;
			this.json = json;
		}

		@Override
		public String type() {
			return "OrderRefunded";
		}

		@Override
		public void handle(OutboxMessage message) {
			RefundedOrder refunded = json.readValue(message.payload(), RefundedOrder.class);
			ledger.post(refunded.organizationId(), refunded.eventId(), refunded.orderId(), EntryType.TICKET_REFUND,
					-(refunded.totalCents() - refunded.feeCents()), refunded.tickets(), refunded.kind(), refunded.refundedAt());
			ledger.post(refunded.organizationId(), refunded.eventId(), refunded.orderId(),
					EntryType.SERVICE_FEE_REFUND, -refunded.feeCents(), 0, refunded.kind(), refunded.refundedAt());
		}

	}

}
