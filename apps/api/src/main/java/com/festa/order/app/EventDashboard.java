package com.festa.order.app;

import com.festa.event.api.EventDirectory;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.Admission;
import com.festa.ticketing.api.Inventory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Painel do evento (PLAN.md M8): vendas pagas, estoque, entradas e vendas por dia (no fuso de São Paulo).
 * Valores em centavos; receita = ingressos, sem a taxa de serviço (que é da plataforma).
 */
@Service
public class EventDashboard {

	private static final Role[] MANAGERS = { Role.OWNER, Role.ADMIN, Role.MANAGER };

	private final JdbcClient jdbc;
	private final TenantGuard tenantGuard;
	private final EventDirectory events;
	private final Inventory inventory;
	private final Admission admission;

	EventDashboard(JdbcClient jdbc, TenantGuard tenantGuard, EventDirectory events, Inventory inventory,
			Admission admission) {
		this.jdbc = jdbc;
		this.tenantGuard = tenantGuard;
		this.events = events;
		this.inventory = inventory;
		this.admission = admission;
	}

	public record Day(LocalDate date, int tickets, long revenueCents) {
	}

	public record Summary(int paidOrders, int ticketsSold, long revenueCents, long feeCents, int pendingOrders,
			Inventory.Stock stock, Admission.Counts entries, List<Day> byDay) {
	}

	@Transactional(readOnly = true)
	public Summary summary(UUID organizationId, UUID eventId, UUID userId) {
		tenantGuard.requireRole(organizationId, userId, MANAGERS);
		events.find(organizationId, eventId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Evento não encontrado."));
		Summary partial = jdbc.sql("""
				SELECT count(*) FILTER (WHERE status = 'PAID') AS paid,
				       coalesce(sum(subtotal_cents) FILTER (WHERE status = 'PAID'), 0) AS revenue,
				       coalesce(sum(fee_cents) FILTER (WHERE status = 'PAID'), 0) AS fees,
				       count(*) FILTER (WHERE status = 'PENDING_PAYMENT') AS pending
				  FROM orders WHERE event_id = :eventId AND organization_id = :organizationId
				""")
			.param("eventId", eventId)
			.param("organizationId", organizationId)
			.query((rs, i) -> new Summary(rs.getInt("paid"), 0, rs.getLong("revenue"), rs.getLong("fees"),
					rs.getInt("pending"), null, null, List.of()))
			.single();
		List<Day> byDay = jdbc.sql("""
				SELECT (o.paid_at AT TIME ZONE 'America/Sao_Paulo')::date AS day,
				       count(i.id) AS tickets, sum(i.unit_price_cents) AS revenue
				  FROM orders o JOIN order_items i ON i.order_id = o.id
				 WHERE o.event_id = :eventId AND o.organization_id = :organizationId AND o.status = 'PAID'
				 GROUP BY day ORDER BY day
				""")
			.param("eventId", eventId)
			.param("organizationId", organizationId)
			.query((rs, i) -> new Day(rs.getObject("day", LocalDate.class), rs.getInt("tickets"), rs.getLong("revenue")))
			.list();
		int sold = byDay.stream().mapToInt(Day::tickets).sum();
		return new Summary(partial.paidOrders(), sold, partial.revenueCents(), partial.feeCents(),
				partial.pendingOrders(), inventory.stock(eventId), admission.counts(eventId), byDay);
	}

}
