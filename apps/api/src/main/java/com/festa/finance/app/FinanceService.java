package com.festa.finance.app;

import com.festa.event.api.EventDirectory;
import com.festa.event.api.EventDirectory.EventRef;
import com.festa.finance.infra.LedgerStore;
import com.festa.finance.infra.LedgerStore.EventTotals;
import com.festa.finance.infra.LedgerStore.Totals;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Financeiro básico (PLAN.md M9): quanto vendeu, quanto foi estornado e quanto é do produtor. Com o split do
 * Mercado Pago (ADR-003) o dinheiro dos ingressos cai direto na conta do produtor a cada venda: a plataforma não
 * repassa nada, então "a receber" é o que o gateway ainda não liberou (fora daqui).
 */
@Service
public class FinanceService {

	private static final Role[] MANAGERS = { Role.OWNER, Role.ADMIN, Role.MANAGER };

	private final LedgerStore ledger;
	private final EventDirectory events;
	private final TenantGuard tenantGuard;

	FinanceService(LedgerStore ledger, EventDirectory events, TenantGuard tenantGuard) {
		this.ledger = ledger;
		this.events = events;
		this.tenantGuard = tenantGuard;
	}

	public record EventFinance(UUID eventId, String name, Instant startsAt, String status, Totals totals) {
	}

	public record Overview(List<EventFinance> events, Totals totals) {
	}

	@Transactional(readOnly = true)
	public EventFinance event(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		EventRef event = events.find(organizationId, eventId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
					"Evento não encontrado."));
		Totals totals = ledger.totals(organizationId, eventId).stream()
			.findFirst()
			.map(EventTotals::totals)
			.orElse(Totals.ZERO);
		return new EventFinance(event.id(), event.name(), event.startsAt(), event.status().name(), totals);
	}

	/** Todos os eventos com venda, do mais recente para o mais antigo. */
	@Transactional(readOnly = true)
	public Overview overview(UUID organizationId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		List<EventFinance> rows = ledger.totals(organizationId, null).stream()
			.flatMap(t -> events.find(organizationId, t.eventId()).stream()
				.map(e -> new EventFinance(e.id(), e.name(), e.startsAt(), e.status().name(), t.totals())))
			.sorted(Comparator.comparing(EventFinance::startsAt, Comparator.nullsLast(Comparator.reverseOrder())))
			.toList();
		Totals sum = rows.stream().map(EventFinance::totals).reduce(Totals.ZERO, Totals::plus);
		return new Overview(rows, sum);
	}

}
