package com.festa.checkin.app;

import com.festa.checkin.infra.CheckinStore;
import com.festa.checkin.infra.CheckinStore.Active;
import com.festa.checkin.infra.CheckinStore.Source;
import com.festa.compliance.api.AuditLog;
import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.event.api.EventStatus;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.Admission;
import com.festa.ticketing.api.Admission.Attendee;
import com.festa.ticketing.api.Admission.ManifestEntry;
import com.festa.ticketing.api.Admission.Outcome;
import com.festa.ticketing.api.Admission.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portaria (PLAN.md M8, ADR-010): validação online, sincronização do modo offline, lista de participantes e
 * desfazer. Regra de conflito: vale a leitura mais antiga; as outras viram duplicadas para o relatório.
 */
@Service
public class CheckinService {

	/** Quem opera a portaria. */
	private static final Role[] OPERATORS = { Role.OWNER, Role.ADMIN, Role.MANAGER, Role.CHECKIN_OPERATOR };
	/** Quem vê a lista completa de participantes (com CPF mascarado e e-mail de quem comprou). */
	private static final Role[] MANAGERS = { Role.OWNER, Role.ADMIN, Role.MANAGER };
	/** Desfazer check-in mexe em quem entrou: só dono e admin (SECURITY.md). */
	private static final Role[] UNDOERS = { Role.OWNER, Role.ADMIN };
	/** Relógio de celular adiantado não grava check-in no futuro. */
	private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private final Admission admission;
	private final CheckinStore checkins;
	private final TenantGuard tenantGuard;
	private final EventDirectory events;
	private final AuditLog audit;
	private final Clock clock;

	CheckinService(Admission admission, CheckinStore checkins, TenantGuard tenantGuard, EventDirectory events,
			AuditLog audit, Clock clock) {
		this.admission = admission;
		this.checkins = checkins;
		this.tenantGuard = tenantGuard;
		this.events = events;
		this.audit = audit;
		this.clock = clock;
	}

	/** Resultado da leitura, com a hora do check-in que vale (agora ou o anterior, se já tinha entrado). */
	public record Scan(Result result, Instant checkedInAt) {
	}

	public enum SyncOutcome {
		/** Gravado (ou já estava gravado, numa sincronização repetida). */
		ACCEPTED,
		/** Outro aparelho leu antes: fica no relatório de conflitos. */
		DUPLICATE,
		/** Ingresso não vale para este evento. */
		INVALID
	}

	public record SyncItem(String tokenHash, UUID ticketId, Instant checkedInAt) {
	}

	public record SyncResult(SyncItem item, SyncOutcome outcome, UUID ticketId) {
	}

	public record Manifest(EventRef event, List<ManifestEntry> entries, Instant generatedAt) {
	}

	/** Participante com o check-in que vale (nulo se não entrou). */
	public record Participant(Attendee attendee, Active checkin) {
	}

	public record Participants(List<Participant> items, Admission.Counts counts, int duplicates, boolean full) {
	}

	@Transactional(readOnly = true)
	public Manifest manifest(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, OPERATORS);
		EventRef event = openEvent(organizationId, eventId);
		return new Manifest(event, admission.manifest(eventId), clock.instant());
	}

	/** Leitura online: pelo token do QR ou, na busca manual, pelo id do ingresso. */
	@Transactional
	public Scan scan(UUID organizationId, UUID eventId, UUID userId, String token, UUID ticketId, String deviceId) {
		tenantGuard.requireRole(organizationId, userId, OPERATORS);
		openEvent(organizationId, eventId);
		Result result = ticketId != null ? admission.admitById(eventId, ticketId) : admission.admitByToken(eventId, token);
		Instant now = clock.instant();
		if (result.outcome() == Outcome.ADMITTED) {
			checkins.insert(result.pass().ticketId(), eventId, organizationId, userId, now, Source.ONLINE, deviceId, false);
			return new Scan(result, now);
		}
		if (result.outcome() == Outcome.ALREADY_IN) {
			return new Scan(result, checkins.active(result.pass().ticketId()).map(Active::checkedInAt).orElse(null));
		}
		return new Scan(result, null);
	}

	/**
	 * Lote do modo offline. Idempotente: o aparelho pode reenviar o mesmo lote. Se dois aparelhos leram o
	 * mesmo ingresso, vale a leitura mais antiga.
	 */
	@Transactional
	public List<SyncResult> sync(UUID organizationId, UUID eventId, UUID userId, String deviceId, List<SyncItem> items) {
		tenantGuard.requireRole(organizationId, userId, OPERATORS);
		openEvent(organizationId, eventId);
		Instant latest = clock.instant().plus(MAX_CLOCK_SKEW);
		return items.stream().map(item -> {
			Instant at = item.checkedInAt().isAfter(latest) ? clock.instant() : item.checkedInAt();
			Result result = item.ticketId() != null ? admission.admitById(eventId, item.ticketId())
					: admission.admitByTokenHash(eventId, item.tokenHash());
			if (result.outcome() == Outcome.NOT_VALID) {
				return new SyncResult(item, SyncOutcome.INVALID, result.pass() == null ? null : result.pass().ticketId());
			}
			UUID ticket = result.pass().ticketId();
			if (result.outcome() == Outcome.ADMITTED) {
				checkins.insert(ticket, eventId, organizationId, userId, at, Source.OFFLINE, deviceId, false);
				return new SyncResult(item, SyncOutcome.ACCEPTED, ticket);
			}
			if (checkins.alreadySynced(ticket, deviceId, at)) {
				return new SyncResult(item, SyncOutcome.ACCEPTED, ticket);
			}
			Active current = checkins.active(ticket).orElse(null);
			if (current != null && at.isBefore(current.checkedInAt())) {
				// Esta leitura foi antes: ela passa a valer e a outra vira duplicada.
				checkins.markDuplicate(current.id());
				checkins.insert(ticket, eventId, organizationId, userId, at, Source.OFFLINE, deviceId, false);
				return new SyncResult(item, SyncOutcome.ACCEPTED, ticket);
			}
			checkins.insert(ticket, eventId, organizationId, userId, at, Source.OFFLINE, deviceId, true);
			return new SyncResult(item, SyncOutcome.DUPLICATE, ticket);
		}).toList();
	}

	/**
	 * Lista do painel (gerência vê CPF mascarado e e-mail) e busca da portaria (operador vê só nome, tipo e
	 * status).
	 */
	@Transactional(readOnly = true)
	public Participants participants(UUID organizationId, UUID eventId, UUID userId, String query, String status,
			int limit) {
		Role role = tenantGuard.requireRole(organizationId, userId, OPERATORS);
		events.find(organizationId, eventId).orElseThrow(CheckinService::eventNotFound);
		List<Attendee> found = admission.attendees(eventId, query, status, limit + 1);
		boolean full = role != Role.CHECKIN_OPERATOR;
		List<Attendee> page = found.subList(0, Math.min(limit, found.size())).stream()
			.map(a -> full ? a : new Attendee(a.pass(), null, null))
			.toList();
		Map<UUID, Active> active = checkins.activeFor(eventId, page.stream().map(a -> a.pass().ticketId()).toList());
		return new Participants(page.stream().map(a -> new Participant(a, active.get(a.pass().ticketId()))).toList(),
				admission.counts(eventId), checkins.duplicates(eventId), found.size() > limit);
	}

	/** Desfaz o check-in que vale (entrada por engano): o ingresso volta a valer e fica no audit log. */
	@Transactional
	public void undo(UUID organizationId, UUID checkinId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, UNDOERS);
		CheckinStore.Row row = checkins.lock(checkinId, organizationId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Check-in não encontrado."));
		if (!row.active()) {
			throw new ApiException(HttpStatus.CONFLICT, "checkin-not-active", "Check-in não está valendo",
				"Esse check-in já foi desfeito ou é uma leitura duplicada.");
		}
		Instant now = clock.instant();
		checkins.undo(checkinId, userId, now);
		admission.readmit(row.eventId(), row.ticketId());
		audit.record(organizationId, userId, "checkin.undone", "ticket", row.ticketId(),
				Map.of("checkinId", checkinId, "eventId", row.eventId()));
	}

	private EventRef openEvent(UUID organizationId, UUID eventId) {
		EventRef event = events.find(organizationId, eventId).orElseThrow(CheckinService::eventNotFound);
		if (event.status() != EventStatus.PUBLISHED && event.status() != EventStatus.ENDED) {
			throw new ApiException(HttpStatus.CONFLICT, "invalid-event-status", "Evento fora da portaria",
				"Só evento publicado tem check-in.");
		}
		return event;
	}

	private static ApiException eventNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Evento não encontrado.");
	}

}
