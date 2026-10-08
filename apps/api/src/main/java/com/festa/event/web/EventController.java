package com.festa.event.web;

import com.festa.event.app.EventService;
import com.festa.event.app.EventService.Act;
import com.festa.event.app.EventService.EventSummary;
import com.festa.event.app.EventService.EventView;
import com.festa.event.app.EventService.Flyer;
import com.festa.event.domain.Event;
import com.festa.event.api.EventStatus;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/events")
class EventController {

	private final EventService events;
	private final String webBaseUrl;

	EventController(EventService events, @Value("${festa.web.base-url}") String webBaseUrl) {
		this.events = events;
		this.webBaseUrl = webBaseUrl;
	}

	record CreateEventRequest(
			@NotBlank(message = "Informe o nome da festa.") @Size(max = 120, message = "Nome muito longo.") String name) {
	}

	record ActRequest(
			@NotBlank(message = "Informe a atração.") @Size(max = 120, message = "Nome muito longo.") String name,
			Instant startsAt) {
	}

	/** Campos ausentes ou nulos ficam como estão; texto vazio apaga campo opcional. */
	record UpdateEventRequest(
			@Pattern(regexp = ".*\\S.*", message = "O nome não pode ficar vazio.")
			@Size(max = 120, message = "Nome muito longo.") String name,
			@Size(max = 5000, message = "Descrição muito longa.") String description,
			@Size(max = 40, message = "Categoria muito longa.") String category,
			Instant startsAt,
			Instant endsAt,
			@Size(max = 120, message = "Nome do local muito longo.") String venueName,
			@Size(max = 300, message = "Endereço muito longo.") String address,
			@Size(max = 80, message = "Cidade muito longa.") String city,
			@Min(value = 0, message = "Idade inválida.") @Max(value = 21, message = "Idade inválida.") Integer minAge,
			Boolean hasOpenBar,
			@Min(value = 0, message = "Use de 0 a 100.") @Max(value = 100, message = "Use de 0 a 100.") Integer halfPriceQuotaPercent,
			/** 0 tira o limite. */
			@Min(value = 0, message = "Use de 1 a 20, ou 0 para sem limite.") @Max(value = 20, message = "Máximo de 20.") Integer maxTicketsPerCpf,
			@Pattern(regexp = "^$|^#[0-9a-f]{6}$", message = "Use o formato #rrggbb.") String accentColor,
			@Size(max = 30, message = "No máximo 30 atrações.") List<@Valid @NotNull ActRequest> lineup) {
	}

	record SetFlyerRequest(
			@NotBlank(message = "Informe o arquivo enviado.") @Size(max = 300) String key,
			@Min(value = 1, message = "Largura inválida.") @Max(value = 20000, message = "Largura inválida.") int width,
			@Min(value = 1, message = "Altura inválida.") @Max(value = 20000, message = "Altura inválida.") int height) {
	}

	record ActResponse(String name, Instant startsAt) {
	}

	record EventResponse(UUID id, String slug, String name, String description, String category, Instant startsAt,
			Instant endsAt, String venueName, String address, String city, int minAge, boolean hasOpenBar,
			int halfPriceQuotaPercent, Integer maxTicketsPerCpf, String accentColor, EventStatus status,
			Instant publishedAt, Flyer flyer, List<ActResponse> lineup, String pageUrl) {
	}

	record EventSummaryResponse(UUID id, String slug, String name, EventStatus status, Instant startsAt,
			String venueName, String flyerUrl) {
	}

	record EventList(List<EventSummaryResponse> items) {
	}

	@PostMapping
	ResponseEntity<EventResponse> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@Valid @RequestBody CreateEventRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(response(events.create(orgId, user.id(), body.name())));
	}

	@GetMapping
	EventList list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@RequestParam(required = false) EventStatus status) {
		return new EventList(events.list(orgId, user.id(), status).stream().map(EventController::summary).toList());
	}

	@GetMapping("/{eventId}")
	EventResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(events.get(orgId, eventId, user.id()));
	}

	@PatchMapping("/{eventId}")
	EventResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest body) {
		Event.Changes changes = new Event.Changes(body.name(), body.description(), body.category(), body.startsAt(),
			body.endsAt(), body.venueName(), body.address(), body.city(), body.minAge(), body.hasOpenBar(),
			body.halfPriceQuotaPercent(), body.maxTicketsPerCpf(), body.accentColor());
		List<Act> acts = body.lineup() == null ? null
				: body.lineup().stream().map(act -> new Act(act.name(), act.startsAt())).toList();
		return response(events.update(orgId, eventId, user.id(), changes, acts));
	}

	/** Passo 2 do envio do flyer: depois do PUT no bucket, liga o arquivo ao evento. */
	@PutMapping("/{eventId}/media/flyer")
	EventResponse setFlyer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @Valid @RequestBody SetFlyerRequest body) {
		return response(events.setFlyer(orgId, eventId, user.id(), body.key(), body.width(), body.height()));
	}

	@PostMapping("/{eventId}/publish")
	EventResponse publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(events.publish(orgId, eventId, user.id()));
	}

	@PostMapping("/{eventId}/end")
	EventResponse end(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(events.end(orgId, eventId, user.id()));
	}

	@PostMapping("/{eventId}/cancel")
	EventResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		return response(events.cancel(orgId, eventId, user.id()));
	}

	private EventResponse response(EventView view) {
		Event e = view.event();
		return new EventResponse(e.getId(), e.getSlug(), e.getName(), e.getDescription(), e.getCategory(),
			e.getStartsAt(), e.getEndsAt(), e.getVenueName(), e.getAddress(), e.getCity(), e.getMinAge(),
			e.isHasOpenBar(), e.getHalfPriceQuotaPercent(), e.getMaxTicketsPerCpf(), e.getAccentColor(),
			e.getStatus(), e.getPublishedAt(), view.flyer(),
			view.lineup().stream().map(act -> new ActResponse(act.name(), act.startsAt())).toList(),
			webBaseUrl + "/e/" + e.getSlug());
	}

	private static EventSummaryResponse summary(EventSummary summary) {
		Event e = summary.event();
		return new EventSummaryResponse(e.getId(), e.getSlug(), e.getName(), e.getStatus(), e.getStartsAt(),
			e.getVenueName(), summary.flyer() == null ? null : summary.flyer().url());
	}

}
