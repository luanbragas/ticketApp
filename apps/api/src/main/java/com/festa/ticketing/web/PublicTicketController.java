package com.festa.ticketing.web;

import com.festa.event.api.EventStatus;
import com.festa.ticketing.app.TicketingService;
import com.festa.ticketing.app.TicketingService.BatchView;
import com.festa.ticketing.app.TicketingService.Catalog;
import com.festa.ticketing.domain.BatchStatus;
import com.festa.ticketing.domain.TicketBatch;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Disponibilidade dos ingressos na página pública (sem login). Fica fora da rota do evento porque muda a
 * cada venda: cache de poucos segundos. Não revela quantos foram vendidos, só se está acabando.
 */
@RestController
@RequestMapping("/api/v1/public/events")
class PublicTicketController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(5)).cachePublic();

	private final TicketingService ticketing;

	PublicTicketController(TicketingService ticketing) {
		this.ticketing = ticketing;
	}

	/** {@code AVAILABLE}, {@code LAST_UNITS} (acabando) ou {@code UNAVAILABLE} (tudo reservado por agora). */
	enum Availability {
		AVAILABLE, LAST_UNITS, UNAVAILABLE
	}

	/** O id do lote vai junto porque o pedido (M4) aponta para ele; é UUID, não dá para adivinhar outros. */
	record PublicBatch(UUID id, String name, long priceCents, BatchStatus status, Availability availability,
			Instant salesStartAt, Instant salesEndAt, int maxPerOrder) {
	}

	record PublicType(String name, String description, boolean halfPrice, List<PublicBatch> batches) {
	}

	record PublicTickets(EventStatus eventStatus, List<PublicType> types) {
	}

	@GetMapping("/{slug}/availability")
	ResponseEntity<PublicTickets> availability(@PathVariable String slug) {
		Catalog catalog = ticketing.publicCatalog(slug);
		PublicTickets body = new PublicTickets(catalog.event().status(), catalog.types().stream()
			.map(t -> new PublicType(t.type().getName(), t.type().getDescription(), t.type().isHalfPrice(),
					t.batches().stream().map(PublicTicketController::batch).toList()))
			.toList());
		return ResponseEntity.ok().cacheControl(CACHE).body(body);
	}

	private static PublicBatch batch(BatchView view) {
		TicketBatch b = view.batch();
		return new PublicBatch(b.getId(), b.getName(), b.getPriceCents(), view.status(), availabilityOf(view),
			b.getSalesStartAt(), b.getSalesEndAt(), b.getEffectiveMaxPerOrder());
	}

	static Availability availabilityOf(BatchView view) {
		if (view.status() != BatchStatus.ON_SALE || view.batch().getRemaining() <= 0) {
			return Availability.UNAVAILABLE;
		}
		int lastUnits = Math.max(5, view.batch().getCapacity() / 10);
		return view.batch().getRemaining() <= lastUnits ? Availability.LAST_UNITS : Availability.AVAILABLE;
	}

}
