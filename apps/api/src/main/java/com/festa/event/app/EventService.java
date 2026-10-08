package com.festa.event.app;

import com.festa.event.domain.Event;
import com.festa.event.domain.EventMedia;
import com.festa.event.domain.EventMedia.Kind;
import com.festa.event.domain.EventRuleException;
import com.festa.event.domain.EventStatus;
import com.festa.event.domain.LineupItem;
import com.festa.event.infra.EventMediaRepository;
import com.festa.event.infra.EventRepository;
import com.festa.event.infra.LineupRepository;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.text.Slugs;
import com.festa.shared.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Casos de uso do evento no painel. Toda operação passa pelo {@link TenantGuard} com a organização da
 * URL e carrega o evento junto com ela: evento de outra organização responde 404.
 */
@Service
public class EventService {

	/** Ver eventos: todo membro (portaria e promoter precisam da lista). */
	private static final Role[] READERS = Role.values();
	/** Criar, editar, publicar e encerrar. */
	private static final Role[] EDITORS = { Role.OWNER, Role.ADMIN, Role.MANAGER };
	/** Cancelar afeta quem já comprou: só dono e admin. */
	private static final Role[] CANCELLERS = { Role.OWNER, Role.ADMIN };

	private static final int MAX_SLUG_SUFFIX = 50;
	private static final Pattern FLYER_KEY = Pattern.compile("^[a-z0-9-]+\\.(jpg|png|webp)$");

	private final EventRepository events;
	private final EventMediaRepository media;
	private final LineupRepository lineup;
	private final TenantGuard tenantGuard;
	private final MediaStorage storage;
	private final Clock clock;

	EventService(EventRepository events, EventMediaRepository media, LineupRepository lineup, TenantGuard tenantGuard,
			MediaStorage storage, Clock clock) {
		this.events = events;
		this.media = media;
		this.lineup = lineup;
		this.tenantGuard = tenantGuard;
		this.storage = storage;
		this.clock = clock;
	}

	public record Flyer(String url, int width, int height) {
	}

	public record Act(String name, Instant startsAt) {
	}

	/** Evento com flyer e programação, como o painel mostra. */
	public record EventView(Event event, Flyer flyer, List<Act> lineup) {
	}

	/** Linha da lista de eventos. */
	public record EventSummary(Event event, Flyer flyer) {
	}

	@Transactional
	public EventView create(UUID organizationId, UUID userId, String name) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		Event event;
		try {
			event = events.saveAndFlush(new Event(organizationId, name, availableSlugFor(name)));
		}
		catch (DataIntegrityViolationException ex) {
			// Outro evento pegou o mesmo slug entre a checagem e o INSERT: o usuário pode tentar de novo.
			throw new ApiException(HttpStatus.CONFLICT, "slug-taken", "Endereço em uso",
				"Já existe um evento com esse endereço. Tente de novo.");
		}
		return new EventView(event, null, List.of());
	}

	@Transactional(readOnly = true)
	public List<EventSummary> list(UUID organizationId, UUID userId, EventStatus status) {
		tenantGuard.requireRole(organizationId, userId, READERS);
		List<Event> found = events.findForOrganization(organizationId, status);
		Map<UUID, Flyer> flyers = media.findByEventIdInAndKind(found.stream().map(Event::getId).toList(), Kind.FLYER)
			.stream()
			.collect(Collectors.toMap(EventMedia::getEventId, EventService::flyerOf, (a, b) -> a));
		return found.stream().map(event -> new EventSummary(event, flyers.get(event.getId()))).toList();
	}

	@Transactional(readOnly = true)
	public EventView get(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, READERS);
		return view(load(organizationId, eventId));
	}

	/** Atualiza os dados; com {@code acts} diferente de {@code null}, troca a programação inteira. */
	@Transactional
	public EventView update(UUID organizationId, UUID eventId, UUID userId, Event.Changes changes, List<Act> acts) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		Event event = load(organizationId, eventId);
		apply(() -> event.update(changes));
		if (acts != null) {
			lineup.deleteByEventId(eventId);
			List<LineupItem> items = new ArrayList<>();
			for (int i = 0; i < acts.size(); i++) {
				items.add(new LineupItem(eventId, organizationId, acts.get(i).name(), acts.get(i).startsAt(), i));
			}
			lineup.saveAll(items);
		}
		return view(event);
	}

	/**
	 * Liga ao evento o flyer já enviado pela URL pré-assinada. A chave precisa estar na pasta do próprio
	 * evento, para ninguém apontar para arquivo de outra organização.
	 */
	@Transactional
	public EventView setFlyer(UUID organizationId, UUID eventId, UUID userId, String key, int width, int height) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		Event event = load(organizationId, eventId);
		apply(event::requireEditable);
		String folder = "orgs/%s/events/%s/flyer/".formatted(organizationId, eventId);
		if (key == null || !key.startsWith(folder) || !FLYER_KEY.matcher(key.substring(folder.length())).matches()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-media-key", "Arquivo inválido",
				"Esse arquivo não foi enviado para este evento.");
		}
		String url = storage.publicUrl(key).toString();
		media.findByEventIdAndKind(eventId, Kind.FLYER).ifPresentOrElse(
				existing -> existing.replace(url, width, height),
				() -> media.save(new EventMedia(eventId, organizationId, Kind.FLYER, url, width, height)));
		return view(event);
	}

	@Transactional
	public EventView publish(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		Event event = load(organizationId, eventId);
		boolean hasFlyer = media.findByEventIdAndKind(eventId, Kind.FLYER).isPresent();
		apply(() -> event.publish(clock.instant(), hasFlyer));
		return view(event);
	}

	@Transactional
	public EventView end(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		Event event = load(organizationId, eventId);
		apply(event::end);
		return view(event);
	}

	@Transactional
	public EventView cancel(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, CANCELLERS);
		Event event = load(organizationId, eventId);
		apply(event::cancel);
		return view(event);
	}

	private Event load(UUID organizationId, UUID eventId) {
		return events.findByIdAndOrganizationId(eventId, organizationId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
	}

	private EventView view(Event event) {
		Flyer flyer = media.findByEventIdAndKind(event.getId(), Kind.FLYER).map(EventService::flyerOf).orElse(null);
		List<Act> acts = lineup.findByEventIdOrderByPosition(event.getId()).stream()
			.map(item -> new Act(item.getName(), item.getStartsAt()))
			.toList();
		return new EventView(event, flyer, acts);
	}

	private static Flyer flyerOf(EventMedia m) {
		return new Flyer(m.getUrl(), m.getWidth(), m.getHeight());
	}

	/** Traduz a regra do domínio para Problem Details: 409 para transição, 422 para dado. */
	private static void apply(Runnable action) {
		try {
			action.run();
		}
		catch (EventRuleException ex) {
			ApiException api = new ApiException(ex.isConflict() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_CONTENT,
				ex.getCode(), ex.isConflict() ? "Estado do evento não permite" : "Regra do evento", ex.getMessage());
			if (!ex.getMissing().isEmpty()) {
				api.withProperty("missing", ex.getMissing());
			}
			throw api;
		}
	}

	private String availableSlugFor(String name) {
		String base = Slugs.fromName(name, Event.SLUG_MAX_LENGTH);
		if (base.length() < Slugs.MIN_LENGTH) {
			base = "evento" + (base.isEmpty() ? "" : "-" + base);
		}
		if (!events.existsBySlug(base)) {
			return base;
		}
		for (int suffix = 2; suffix <= MAX_SLUG_SUFFIX; suffix++) {
			String slug = Slugs.withSuffix(base, suffix, Event.SLUG_MAX_LENGTH);
			if (!events.existsBySlug(slug)) {
				return slug;
			}
		}
		return Slugs.withSuffix(base, (int) (clock.millis() % 1_000_000), Event.SLUG_MAX_LENGTH);
	}

}
