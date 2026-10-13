package com.festa.ticketing.web;

import com.festa.event.api.EventDirectory.EventRef;
import com.festa.identity.api.UserDirectory;
import com.festa.shared.security.AuthenticatedUser;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.app.TicketQueries;
import com.festa.ticketing.app.TicketQueries.TicketView;
import com.festa.ticketing.domain.Ticket;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Ingresso para quem comprou: a página do QR (quem tem o token vê) e "Meus ingressos" (sessão com e-mail
 * verificado, ADR-004). Sem cache: status muda no check-in e na transferência.
 */
@RestController
class TicketController {

	private final TicketQueries queries;
	private final UserDirectory users;
	private final Clock clock;

	TicketController(TicketQueries queries, UserDirectory users, Clock clock) {
		this.queries = queries;
		this.users = users;
		this.clock = clock;
	}

	record EventResponse(String slug, String name, Instant startsAt, Instant endsAt, String venueName, String address,
			String city, String accentColor) {
	}

	/** Sem ids internos: o token é o próprio endereço do ingresso. */
	record TicketResponse(String token, Ticket.Status status, String holderName, String holderCpf, boolean halfPrice,
			String typeName, String batchName, EventResponse event) {
	}

	record MyTickets(List<TicketResponse> upcoming, List<TicketResponse> past) {
	}

	@GetMapping("/api/v1/public/tickets/{token}")
	ResponseEntity<TicketResponse> byToken(@PathVariable String token) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("Referrer-Policy", "no-referrer")
			.body(response(queries.byToken(token)));
	}

	@GetMapping("/api/v1/me/tickets")
	ResponseEntity<MyTickets> mine(@AuthenticationPrincipal AuthenticatedUser user) {
		UserDirectory.UserSummary me = users.find(user.id()).orElseThrow();
		if (!me.emailVerified()) {
			// Conta com senha e e-mail nunca confirmado: pode ser e-mail de outra pessoa.
			throw new ApiException(HttpStatus.FORBIDDEN, "email-not-verified", "E-mail não confirmado",
				"Entre pelo link mágico enviado ao seu e-mail para ver os ingressos.");
		}
		Instant now = clock.instant();
		List<TicketResponse> all = queries.forBuyer(me.email()).stream().map(TicketController::response).toList();
		List<TicketResponse> upcoming = all.stream().filter(t -> t.event().endsAt().isAfter(now))
			.sorted((a, b) -> a.event().startsAt().compareTo(b.event().startsAt()))
			.toList();
		List<TicketResponse> past = all.stream().filter(t -> !t.event().endsAt().isAfter(now))
			.sorted((a, b) -> b.event().startsAt().compareTo(a.event().startsAt()))
			.toList();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MyTickets(upcoming, past));
	}

	private static TicketResponse response(TicketView view) {
		EventRef e = view.event();
		return new TicketResponse(view.token(), view.status(), view.holderName(), view.holderCpf(), view.halfPrice(),
			view.typeName(), view.batchName(), new EventResponse(e.slug(), e.name(), e.startsAt(), e.endsAt(),
					e.venueName(), e.address(), e.city(), e.accentColor()));
	}

}
