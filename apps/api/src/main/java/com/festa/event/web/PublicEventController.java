package com.festa.event.web;

import com.festa.event.app.EventService;
import com.festa.event.app.EventService.Flyer;
import com.festa.event.app.EventService.PublicEventView;
import com.festa.event.domain.Event;
import com.festa.event.domain.EventStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Página pública do evento (sem login). Não expõe ids internos nem dados da equipe; a disponibilidade
 * dos ingressos vem em rota própria quando o M3 existir, porque muda rápido e não pode ir para o cache.
 */
@RestController
@RequestMapping("/api/v1/public/events")
class PublicEventController {

	/** Cache curto na CDN: edição do produtor aparece em até 1 minuto. */
	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(60))
		.staleWhileRevalidate(Duration.ofMinutes(5))
		.cachePublic();

	private final EventService events;

	PublicEventController(EventService events) {
		this.events = events;
	}

	record Organizer(String name, String slug, String logoUrl, String instagram) {
	}

	record ActResponse(String name, Instant startsAt) {
	}

	record PublicEventResponse(String slug, String name, String description, Instant startsAt, Instant endsAt,
			String venueName, String address, String city, int minAge, boolean hasOpenBar, String accentColor,
			EventStatus status, Flyer flyer, List<ActResponse> lineup, Organizer organizer) {
	}

	@GetMapping("/{slug}")
	ResponseEntity<PublicEventResponse> get(@PathVariable String slug) {
		PublicEventView found = events.findPublic(slug);
		Event e = found.view().event();
		var org = found.organization();
		PublicEventResponse body = new PublicEventResponse(e.getSlug(), e.getName(), e.getDescription(),
			e.getStartsAt(), e.getEndsAt(), e.getVenueName(), e.getAddress(), e.getCity(), e.getMinAge(),
			e.isHasOpenBar(), e.getAccentColor(), e.getStatus(), found.view().flyer(),
			found.view().lineup().stream().map(act -> new ActResponse(act.name(), act.startsAt())).toList(),
			new Organizer(org.name(), org.slug(), org.logoUrl(), org.instagram()));
		return ResponseEntity.ok().cacheControl(CACHE).body(body);
	}

}
