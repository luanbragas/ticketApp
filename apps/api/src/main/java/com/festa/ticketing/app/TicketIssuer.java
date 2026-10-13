package com.festa.ticketing.app;

import com.festa.order.api.OrderPaid;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import com.festa.shared.outbox.OutboxPublisher;
import com.festa.ticketing.domain.Ticket;
import com.festa.ticketing.domain.TicketTokens;
import com.festa.ticketing.infra.TicketRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pedido pago → um ingresso por item (ARCHITECTURE.md §Pagamento, passo 5). Idempotente: item que já virou
 * ingresso é pulado, e o banco garante um ingresso por item ({@code order_item_id UNIQUE}). Depois de emitir,
 * publica {@link #ISSUED} para o e-mail sair numa entrega separada (falha no e-mail não reemite).
 */
@Component
class TicketIssuer implements OutboxHandler {

	static final String ISSUED = "TicketsIssued";

	/** Payload de {@link #ISSUED}. */
	record TicketsIssued(UUID orderId) {
	}

	private final TicketRepository tickets;
	private final TicketTokens tokens;
	private final OutboxPublisher outbox;
	private final JsonMapper json;

	TicketIssuer(TicketRepository tickets, TicketTokens tokens, OutboxPublisher outbox, JsonMapper json) {
		this.tickets = tickets;
		this.tokens = tokens;
		this.outbox = outbox;
		this.json = json;
	}

	@Override
	public String type() {
		return OrderPaid.TYPE;
	}

	@Override
	public void handle(OutboxMessage message) {
		OrderPaid paid = json.readValue(message.payload(), OrderPaid.class);
		Set<UUID> issued = tickets.findByOrderItemIdIn(paid.items().stream().map(OrderPaid.Item::orderItemId).toList())
			.stream()
			.map(Ticket::getOrderItemId)
			.collect(Collectors.toSet());
		List<Ticket> fresh = paid.items().stream()
			.filter(item -> !issued.contains(item.orderItemId()))
			.map(item -> {
				byte[] nonce = TicketTokens.newNonce();
				return new Ticket(item.orderItemId(), paid.orderId(), paid.eventId(), paid.organizationId(), item.batchId(),
						paid.buyerEmail(),
						new Ticket.Holder(item.holderName(), Base64.getDecoder().decode(item.holderCpfEncrypted()),
								item.holderCpfHash(), item.halfPrice()),
						nonce, TicketTokens.hash(tokens.tokenFor(nonce)));
			})
			.toList();
		if (fresh.isEmpty()) {
			return;
		}
		tickets.saveAll(fresh);
		outbox.publish(ISSUED, paid.orderId(), new TicketsIssued(paid.orderId()));
	}

}
