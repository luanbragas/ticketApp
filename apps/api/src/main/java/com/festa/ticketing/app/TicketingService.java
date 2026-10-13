package com.festa.ticketing.app;

import com.festa.compliance.api.AuditLog;
import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.event.api.EventStatus;
import com.festa.event.api.PublishPrerequisite;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.ticketing.domain.BatchRollover;
import com.festa.ticketing.domain.BatchStatus;
import com.festa.ticketing.domain.HalfPriceQuota;
import com.festa.ticketing.domain.TicketBatch;
import com.festa.ticketing.domain.TicketRuleException;
import com.festa.ticketing.domain.TicketType;
import com.festa.ticketing.infra.TicketBatchRepository;
import com.festa.ticketing.infra.TicketTypeRepository;
import com.festa.shared.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tipos de ingresso e lotes do evento. Toda escrita trava os lotes do evento, aplica a mudança e roda a
 * virada (ADR-006) na mesma transação; o painel e a página pública sempre veem o status já virado.
 */
@Service
public class TicketingService implements PublishPrerequisite {

	private static final Role[] READERS = Role.values();
	private static final Role[] EDITORS = { Role.OWNER, Role.ADMIN, Role.MANAGER };
	private static final List<BatchStatus> OPEN = List.of(BatchStatus.SCHEDULED, BatchStatus.ON_SALE);

	private final TicketTypeRepository types;
	private final TicketBatchRepository batches;
	private final TenantGuard tenantGuard;
	private final EventDirectory eventDirectory;
	private final AuditLog audit;
	private final Clock clock;

	TicketingService(TicketTypeRepository types, TicketBatchRepository batches, TenantGuard tenantGuard,
			EventDirectory eventDirectory, AuditLog audit, Clock clock) {
		this.types = types;
		this.batches = batches;
		this.tenantGuard = tenantGuard;
		this.eventDirectory = eventDirectory;
		this.audit = audit;
		this.clock = clock;
	}

	/** Lote com o status que vale agora (pode estar à frente do gravado até o job rodar). */
	public record BatchView(TicketBatch batch, BatchStatus status) {
	}

	public record TypeView(TicketType type, List<BatchView> batches) {
	}

	public record Catalog(EventRef event, List<TypeView> types, HalfPriceQuota quota) {
	}

	@Transactional(readOnly = true)
	public Catalog catalog(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, READERS);
		return catalogOf(event(organizationId, eventId));
	}

	@Transactional
	public Catalog createType(UUID organizationId, UUID eventId, UUID userId, String name, String description,
			boolean halfPrice) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		saveRacing(() -> types.saveAndFlush(
				new TicketType(eventId, organizationId, name, description, halfPrice, types.nextPosition(eventId))));
		return catalogOf(event);
	}

	@Transactional
	public Catalog updateType(UUID organizationId, UUID eventId, UUID typeId, UUID userId, String name,
			String description) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		type(event, typeId).update(name, description);
		return catalogOf(event);
	}

	/** Só sai tipo sem venda; leva junto os lotes dele. */
	@Transactional
	public Catalog deleteType(UUID organizationId, UUID eventId, UUID typeId, UUID userId) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		TicketType type = type(event, typeId);
		List<TicketBatch> locked = batches.lockForEvent(eventId, organizationId);
		List<TicketBatch> ofType = locked.stream().filter(b -> b.getTicketTypeId().equals(typeId)).toList();
		rule(() -> ofType.forEach(TicketBatch::requireRemovable));
		batches.deleteAll(ofType);
		types.delete(type);
		return catalogOf(event);
	}

	@Transactional
	public Catalog createBatch(UUID organizationId, UUID eventId, UUID typeId, UUID userId, TicketBatch.Terms terms) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		TicketType type = type(event, typeId);
		List<TicketBatch> locked = new ArrayList<>(batches.lockForEvent(eventId, organizationId));
		TicketBatch batch = rule(() -> new TicketBatch(type, terms, batches.nextPosition(typeId)));
		saveRacing(() -> batches.saveAndFlush(batch));
		locked.add(batch);
		rollover(locked);
		return catalogOf(event);
	}

	@Transactional
	public Catalog updateBatch(UUID organizationId, UUID eventId, UUID batchId, UUID userId, TicketBatch.Terms terms) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		List<TicketBatch> locked = batches.lockForEvent(eventId, organizationId);
		TicketBatch batch = batchIn(locked, batchId);
		long oldPrice = batch.getPriceCents();
		int oldCapacity = batch.getCapacity();
		rule(() -> batch.update(terms));
		if (oldPrice != batch.getPriceCents() || oldCapacity != batch.getCapacity()) {
			// SECURITY.md §Auditoria: preço e capacidade de lote mexem em dinheiro e estoque.
			audit.record(organizationId, userId, "batch.terms-changed", "ticket_batch", batchId,
					Map.of("eventId", eventId, "priceCents", List.of(oldPrice, batch.getPriceCents()),
							"capacity", List.of(oldCapacity, batch.getCapacity())));
		}
		rollover(locked);
		return catalogOf(event);
	}

	/** Encerramento manual: o próximo lote do tipo abre (se a data dele deixar). */
	@Transactional
	public Catalog closeBatch(UUID organizationId, UUID eventId, UUID batchId, UUID userId) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		List<TicketBatch> locked = batches.lockForEvent(eventId, organizationId);
		rule(() -> batchIn(locked, batchId).close());
		rollover(locked);
		return catalogOf(event);
	}

	@Transactional
	public Catalog deleteBatch(UUID organizationId, UUID eventId, UUID batchId, UUID userId) {
		EventRef event = editableEvent(organizationId, eventId, userId);
		List<TicketBatch> locked = new ArrayList<>(batches.lockForEvent(eventId, organizationId));
		TicketBatch batch = batchIn(locked, batchId);
		rule(batch::requireRemovable);
		batches.delete(batch);
		locked.remove(batch);
		rollover(locked);
		return catalogOf(event);
	}

	/** Ingressos da página pública: só lotes visíveis, de evento publicado. Encerrado não tem venda. */
	@Transactional(readOnly = true)
	public Catalog publicCatalog(String slug) {
		EventRef event = eventDirectory.findPublic(slug)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
		if (event.status() != EventStatus.PUBLISHED) {
			return new Catalog(event, List.of(), HalfPriceQuota.of(event.halfPriceQuotaPercent(), List.of()));
		}
		Catalog full = catalogOf(event);
		List<TypeView> visible = full.types().stream()
			.map(t -> new TypeView(t.type(), t.batches().stream().filter(b -> b.batch().isVisible()).toList()))
			.filter(t -> !t.batches().isEmpty())
			.toList();
		return new Catalog(event, visible, full.quota());
	}

	/** Publicar exige ao menos um lote que ainda vai vender. */
	@Override
	@Transactional(readOnly = true)
	public List<String> missingFor(UUID organizationId, UUID eventId) {
		return batches.existsByEventIdAndStatusIn(eventId, OPEN) ? List.of() : List.of("tickets");
	}

	/** Virada de um evento, chamada pelo job de data. Devolve quantos lotes mudaram. */
	@Transactional
	public int rolloverEvent(UUID organizationId, UUID eventId) {
		return rollover(batches.lockForEvent(eventId, organizationId));
	}

	/** Eventos com lote para virar agora (ver {@link TicketBatchRepository#findEventsDueForRollover}). */
	@Transactional(readOnly = true)
	public List<UUID[]> eventsDueForRollover() {
		return batches.findEventsDueForRollover(clock.instant()).stream()
			.map(row -> new UUID[] { (UUID) row[0], (UUID) row[1] })
			.toList();
	}

	private int rollover(List<TicketBatch> eventBatches) {
		Map<UUID, BatchStatus> changes = evaluate(eventBatches, clock.instant());
		eventBatches.forEach(b -> {
			BatchStatus next = changes.get(b.getId());
			if (next != null) {
				b.moveTo(next);
			}
		});
		return changes.size();
	}

	private static Map<UUID, BatchStatus> evaluate(List<TicketBatch> eventBatches, Instant now) {
		Map<UUID, BatchStatus> changes = new HashMap<>();
		eventBatches.stream()
			.collect(Collectors.groupingBy(TicketBatch::getTicketTypeId))
			.values()
			.forEach(group -> changes.putAll(BatchRollover.evaluate(group.stream().map(TicketBatch::slot).toList(), now)));
		return changes;
	}

	private Catalog catalogOf(EventRef event) {
		List<TicketType> eventTypes = types.findByEventIdAndOrganizationIdOrderByPosition(event.id(),
				event.organizationId());
		List<TicketBatch> eventBatches = batches.findByEventIdAndOrganizationIdOrderByPosition(event.id(),
				event.organizationId());
		Map<UUID, BatchStatus> pending = evaluate(eventBatches, clock.instant());
		Map<UUID, List<TicketBatch>> byType = eventBatches.stream()
			.collect(Collectors.groupingBy(TicketBatch::getTicketTypeId));
		Map<UUID, Boolean> halfPrice = eventTypes.stream()
			.collect(Collectors.toMap(TicketType::getId, TicketType::isHalfPrice));
		List<TypeView> views = eventTypes.stream()
			.map(type -> new TypeView(type, byType.getOrDefault(type.getId(), List.of()).stream()
				.map(b -> new BatchView(b, pending.getOrDefault(b.getId(), b.getStatus())))
				.toList()))
			.toList();
		HalfPriceQuota quota = HalfPriceQuota.of(event.halfPriceQuotaPercent(), views.stream()
			.flatMap(t -> t.batches().stream())
			.map(v -> new HalfPriceQuota.Line(halfPrice.get(v.batch().getTicketTypeId()), v.status(),
					v.batch().getCapacity(), v.batch().getSold(), v.batch().getReserved()))
			.toList());
		return new Catalog(event, views, quota);
	}

	private EventRef event(UUID organizationId, UUID eventId) {
		return eventDirectory.find(organizationId, eventId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
	}

	private EventRef editableEvent(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, EDITORS);
		EventRef event = event(organizationId, eventId);
		if (!event.editable()) {
			throw new ApiException(HttpStatus.CONFLICT, "invalid-event-status", "Estado do evento não permite",
				"Evento encerrado ou cancelado não muda os ingressos.");
		}
		return event;
	}

	private TicketType type(EventRef event, UUID typeId) {
		return types.findByIdAndEventIdAndOrganizationId(typeId, event.id(), event.organizationId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Tipo de ingresso não encontrado."));
	}

	private static TicketBatch batchIn(List<TicketBatch> locked, UUID batchId) {
		return locked.stream()
			.filter(b -> b.getId().equals(batchId))
			.findFirst()
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Lote não encontrado."));
	}

	/** Dois cadastros ao mesmo tempo disputam a mesma posição: um deles tenta de novo. */
	private static void saveRacing(Runnable save) {
		try {
			save.run();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "concurrent-change", "Alteração simultânea",
				"Outra pessoa mexeu nos ingressos agora. Tente de novo.");
		}
	}

	private static void rule(Runnable action) {
		rule(() -> {
			action.run();
			return null;
		});
	}

	/** Traduz a regra do domínio para Problem Details: 409 para estado, 422 para dado. */
	private static <T> T rule(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (TicketRuleException ex) {
			throw new ApiException(ex.isConflict() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_CONTENT,
				ex.getCode(), ex.isConflict() ? "Estado do lote não permite" : "Regra do lote", ex.getMessage());
		}
	}

}
