package com.festa.order.app;

import com.festa.event.api.EventDirectory;
import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import com.festa.order.api.OrderRefunded;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Avisa o comprador que o pedido foi reembolsado e os ingressos cancelados. Entrega separada do
 * {@code OrderRefunded}: se o e-mail cair, só ele é tentado de novo, sem segurar o cancelamento dos ingressos e o
 * financeiro. Chargeback não gera e-mail: quem contestou já sabe, e o aviso poderia atrapalhar a disputa.
 */
@Component
class RefundEmailHandler implements OutboxHandler {

	private final EmailSender email;
	private final EventDirectory events;
	private final JsonMapper json;

	RefundEmailHandler(EmailSender email, EventDirectory events, JsonMapper json) {
		this.email = email;
		this.events = events;
		this.json = json;
	}

	/** Publicado junto com {@code OrderRefunded}, só no reembolso; o payload é o mesmo. */
	static final String TYPE = "RefundEmail";

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public void handle(OutboxMessage message) {
		OrderRefunded refunded = json.readValue(message.payload(), OrderRefunded.class);
		String eventName = events.find(refunded.organizationId(), refunded.eventId())
			.map(EventDirectory.EventRef::name)
			.orElse("a festa");
		String amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
			.format(BigDecimal.valueOf(refunded.totalCents(), 2));
		email.send(new EmailMessage(refunded.buyerEmail(), "Pedido reembolsado: " + eventName, """
				Oi, %s.

				Seu pedido para %s foi reembolsado: %s voltam pelo mesmo meio de pagamento.
				No PIX, o dinheiro volta para a conta que pagou; no cartão, o prazo depende do banco emissor.

				Os ingressos desse pedido foram cancelados e não entram mais na festa.
				""".formatted(firstName(refunded.buyerName()), eventName, amount)));
	}

	private static String firstName(String name) {
		String trimmed = name.strip();
		int space = trimmed.indexOf(' ');
		return space > 0 ? trimmed.substring(0, space) : trimmed;
	}

}
