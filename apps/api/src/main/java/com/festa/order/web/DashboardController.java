package com.festa.order.web;

import com.festa.order.app.EventDashboard;
import com.festa.order.app.EventDashboard.Summary;
import com.festa.shared.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Painel do evento: dono, admin e gerente (SECURITY.md). */
@RestController
class DashboardController {

	private final EventDashboard dashboard;

	DashboardController(EventDashboard dashboard) {
		this.dashboard = dashboard;
	}

	record DayResponse(LocalDate date, int tickets, long revenueCents) {
	}

	record DashboardResponse(int paidOrders, int ticketsSold, long revenueCents, long feeCents, int pendingOrders,
			int capacity, int sold, int reserved, int issued, int checkedIn, List<DayResponse> byDay) {
	}

	@GetMapping("/api/v1/orgs/{orgId}/events/{eventId}/dashboard")
	ResponseEntity<DashboardResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId) {
		Summary s = dashboard.summary(orgId, eventId, user.id());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DashboardResponse(s.paidOrders(),
				s.ticketsSold(), s.revenueCents(), s.feeCents(), s.pendingOrders(), s.stock().offered(), s.stock().sold(),
				s.stock().reserved(), s.entries().issued(), s.entries().checkedIn(),
				s.byDay().stream().map(d -> new DayResponse(d.date(), d.tickets(), d.revenueCents())).toList()));
	}

}
