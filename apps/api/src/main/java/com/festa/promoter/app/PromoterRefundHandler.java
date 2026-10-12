package com.festa.promoter.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.festa.promoter.infra.PromoterSalesStore;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/** Pedido estornado → a venda sai da conta do promoter. Contrato próprio do JSON, como no {@link PromoterSalesHandler}. */
@Component
class PromoterRefundHandler implements OutboxHandler {

	/** Mesmo nome de {@code OrderRefunded.TYPE}. */
	static final String ORDER_REFUNDED = "OrderRefunded";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RefundedOrder(UUID orderId, UUID promoterId, Instant refundedAt) {
	}

	private final PromoterSalesStore sales;
	private final JsonMapper json;

	PromoterRefundHandler(PromoterSalesStore sales, JsonMapper json) {
		this.sales = sales;
		this.json = json;
	}

	@Override
	public String type() {
		return ORDER_REFUNDED;
	}

	@Override
	public void handle(OutboxMessage message) {
		RefundedOrder refunded = json.readValue(message.payload(), RefundedOrder.class);
		if (refunded.promoterId() != null) {
			sales.refunded(refunded.orderId(), refunded.refundedAt());
		}
	}

}
