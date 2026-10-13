package com.festa.ticketing.app;

import com.festa.order.api.OrderRefunded;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import com.festa.ticketing.api.OrderTickets;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Pedido estornado → ingressos válidos viram {@code CANCELLED} (ARCHITECTURE.md §Máquinas de estado) e o QR para
 * de abrir a porta. Quem já entrou fica como está: chargeback de quem usou o ingresso é caso para o financeiro.
 * Idempotente: o {@code UPDATE} só pega o que ainda está válido.
 */
@Component
class TicketCancellation implements OutboxHandler, OrderTickets {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	TicketCancellation(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public String type() {
		return OrderRefunded.TYPE;
	}

	@Override
	public void handle(OutboxMessage message) {
		OrderRefunded refunded = json.readValue(message.payload(), OrderRefunded.class);
		jdbc.sql("""
				UPDATE tickets SET status = 'CANCELLED', updated_at = now()
				 WHERE order_id = :orderId AND status = 'VALID'
				""")
			.param("orderId", refunded.orderId())
			.update();
	}

	@Override
	public int checkedIn(UUID orderId) {
		return jdbc.sql("SELECT count(*) FROM tickets WHERE order_id = :orderId AND status = 'CHECKED_IN'")
			.param("orderId", orderId)
			.query(Integer.class)
			.single();
	}

}
