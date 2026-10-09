package com.festa.promoter.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.festa.promoter.infra.PromoterSalesStore;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido pago com promoter → conta a venda para ele (ARCHITECTURE.md §Pagamento, passo 5). Lê o JSON do
 * evento com um contrato próprio, só com o que precisa: o módulo promoter não depende do de pedidos.
 */
@Component
class PromoterSalesHandler implements OutboxHandler {

	/** Mesmo nome de {@code OrderPaid.TYPE}. */
	static final String ORDER_PAID = "OrderPaid";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PaidOrder(UUID orderId, UUID eventId, UUID organizationId, UUID promoterId, long subtotalCents,
			Instant paidAt, List<Object> items) {
	}

	private final PromoterSalesStore sales;
	private final JsonMapper json;

	PromoterSalesHandler(PromoterSalesStore sales, JsonMapper json) {
		this.sales = sales;
		this.json = json;
	}

	@Override
	public String type() {
		return ORDER_PAID;
	}

	@Override
	public void handle(OutboxMessage message) {
		PaidOrder paid = json.readValue(message.payload(), PaidOrder.class);
		if (paid.promoterId() == null) {
			return;
		}
		sales.record(paid.orderId(), paid.promoterId(), paid.eventId(), paid.organizationId(), paid.items().size(),
				paid.subtotalCents(), paid.paidAt());
	}

}
