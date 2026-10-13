package com.festa.finance.web;

import com.festa.finance.app.FinanceService;
import com.festa.finance.app.FinanceService.EventFinance;
import com.festa.finance.infra.LedgerStore.Totals;
import com.festa.shared.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Financeiro da organização e de cada evento: dono, admin e gerente. */
@RestController
class FinanceController {

	private final FinanceService finance;

	FinanceController(FinanceService finance) {
		this.finance = finance;
	}

	/**
	 * Centavos. {@code netCents}: do produtor (ingressos − estornos), antes das tarifas do Mercado Pago, que
	 * ainda não aparecem aqui. {@code serviceFee*}: taxa paga pelo comprador, da plataforma.
	 */
	record TotalsResponse(long ticketSalesCents, long ticketRefundsCents, long netCents, int ticketsSold,
			int ticketsRefunded, int paidOrders, int refundedOrders, int chargebacks, long serviceFeeCents,
			long serviceFeeRefundsCents) {

		static TotalsResponse of(Totals t) {
			return new TotalsResponse(t.ticketSalesCents(), t.ticketRefundsCents(),
					t.ticketSalesCents() - t.ticketRefundsCents(), t.ticketsSold(), t.ticketsRefunded(), t.paidOrders(),
					t.refundedOrders(), t.chargebacks(), t.serviceFeeCents(), t.serviceFeeRefundsCents());
		}

	}

	record EventFinanceResponse(UUID eventId, String name, Instant startsAt, String status, TotalsResponse totals) {

		static EventFinanceResponse of(EventFinance e) {
			return new EventFinanceResponse(e.eventId(), e.name(), e.startsAt(), e.status(),
					TotalsResponse.of(e.totals()));
		}

	}

	record OverviewResponse(List<EventFinanceResponse> events, TotalsResponse totals) {
	}

	@GetMapping("/api/v1/orgs/{orgId}/finance")
	ResponseEntity<OverviewResponse> overview(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId) {
		FinanceService.Overview o = finance.overview(orgId, user.id());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new OverviewResponse(
				o.events().stream().map(EventFinanceResponse::of).toList(), TotalsResponse.of(o.totals())));
	}

	@GetMapping("/api/v1/orgs/{orgId}/events/{eventId}/finance")
	ResponseEntity<EventFinanceResponse> event(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID orgId, @PathVariable UUID eventId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(EventFinanceResponse.of(finance.event(orgId, eventId, user.id())));
	}

}
