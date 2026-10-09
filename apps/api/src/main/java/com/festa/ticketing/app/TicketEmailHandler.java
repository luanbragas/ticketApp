package com.festa.ticketing.app;

import com.festa.event.api.EventDirectory.EventRef;
import com.festa.notification.api.EmailMessage;
import com.festa.notification.api.EmailSender;
import com.festa.shared.outbox.OutboxHandler;
import com.festa.shared.outbox.OutboxMessage;
import com.festa.ticketing.app.TicketIssuer.TicketsIssued;
import com.festa.ticketing.app.TicketQueries.TicketView;
import com.festa.ticketing.domain.Ticket;
import com.festa.ticketing.infra.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * E-mail com os ingressos do pedido (PLAN.md M6). Entrega separada da emissão: se o provedor de e-mail cair,
 * só o e-mail é tentado de novo. Pode sair duas vezes num caso raro (enviou e a transação falhou depois);
 * isso é aceitável, perder o e-mail não é.
 */
@Component
class TicketEmailHandler implements OutboxHandler {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter WHEN = DateTimeFormatter
		.ofPattern("EEEE, d 'de' MMMM 'às' HH'h'mm", Locale.forLanguageTag("pt-BR"))
		.withZone(SAO_PAULO);

	private final TicketQueries queries;
	private final TicketRepository tickets;
	private final EmailSender email;
	private final JsonMapper json;
	private final String webBaseUrl;

	TicketEmailHandler(TicketQueries queries, TicketRepository tickets, EmailSender email, JsonMapper json,
			@Value("${festa.web.base-url}") String webBaseUrl) {
		this.queries = queries;
		this.tickets = tickets;
		this.email = email;
		this.json = json;
		this.webBaseUrl = webBaseUrl;
	}

	@Override
	public String type() {
		return TicketIssuer.ISSUED;
	}

	@Override
	public void handle(OutboxMessage message) {
		TicketsIssued issued = json.readValue(message.payload(), TicketsIssued.class);
		List<Ticket> orderTickets = tickets.findByOrderIdOrderByCreatedAt(issued.orderId());
		List<TicketView> views = queries.forOrder(issued.orderId());
		if (views.isEmpty()) {
			return;
		}
		EventRef event = views.getFirst().event();
		String to = orderTickets.getFirst().getBuyerEmail();
		email.send(new EmailMessage(to, "Seus ingressos: " + event.name(), body(event, views)));
	}

	private String body(EventRef event, List<TicketView> views) {
		String where = Stream.of(event.venueName(), event.address(), event.city())
			.filter(part -> part != null && !part.isBlank())
			.collect(Collectors.joining(" · "));
		StringBuilder text = new StringBuilder()
			.append("Pagamento confirmado. Te vemos na ").append(event.name()).append("!\n\n")
			.append(capitalize(WHEN.format(event.startsAt()))).append('\n')
			.append(where).append("\n\n")
			.append(views.size() == 1 ? "Seu ingresso:\n" : "Seus ingressos:\n");
		for (TicketView view : views) {
			text.append("\n• ").append(view.holderName()).append(" — ").append(view.typeName()).append(" · ")
				.append(view.batchName()).append(view.halfPrice() ? " (meia: leve o documento)" : "").append('\n')
				.append("  ").append(webBaseUrl).append("/t/").append(view.token()).append('\n');
		}
		return text
			.append("\nNa entrada, mostre o QR Code de cada ingresso e um documento com foto do titular.\n")
			.append("Não compartilhe estes links: quem tiver o QR entra no lugar do titular.\n\n")
			.append("Todos os seus ingressos: ").append(webBaseUrl).append("/meus-ingressos\n")
			.toString();
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

}
