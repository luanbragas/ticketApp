package com.festa.payment.app;

import com.festa.compliance.api.AuditLog;
import com.festa.order.api.OrderBilling;
import com.festa.order.api.OrderBilling.BillableOrder;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.payment.domain.Payment;
import com.festa.payment.infra.PaymentRepository;
import com.festa.payment.infra.RefundStore;
import com.festa.shared.id.UuidV7;
import com.festa.shared.web.ApiException;
import com.festa.ticketing.api.OrderTickets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Reembolso manual pelo produtor (PLAN.md M9): estorna no gateway e só então o pedido vira {@code REFUNDED}; os
 * ingressos são cancelados pelo evento {@code OrderRefunded}. Só reembolso total (parcial fica para depois).
 */
@Service
public class RefundService {

	private final PaymentRepository payments;
	private final PaymentAccountService accounts;
	private final PaymentGateway gateway;
	private final OrderBilling orders;
	private final OrderTickets tickets;
	private final RefundStore refunds;
	private final TenantGuard tenantGuard;
	private final AuditLog audit;

	RefundService(PaymentRepository payments, PaymentAccountService accounts, PaymentGateway gateway,
			OrderBilling orders, OrderTickets tickets, RefundStore refunds, TenantGuard tenantGuard, AuditLog audit) {
		this.payments = payments;
		this.accounts = accounts;
		this.gateway = gateway;
		this.orders = orders;
		this.tickets = tickets;
		this.refunds = refunds;
		this.tenantGuard = tenantGuard;
		this.audit = audit;
	}

	public record Refunded(UUID orderId, long amountCents) {
	}

	@Transactional
	public Refunded refund(UUID organizationId, UUID orderId, UUID userId, String reason) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN);
		BillableOrder order = orders.get(orderId)
			.filter(o -> o.organizationId().equals(organizationId))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
					"Pedido não encontrado."));
		// Trava o pagamento: dois cliques (ou clique + webhook) não estornam duas vezes.
		Payment payment = payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
			.filter(p -> p.getStatus() == Payment.Status.APPROVED)
			.findFirst()
			.flatMap(p -> payments.lockByProviderId(p.getProvider(), p.getProviderPaymentId()))
			.filter(p -> p.getStatus() == Payment.Status.APPROVED)
			.orElse(null);
		if (payment == null || !"PAID".equals(order.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "order-not-refundable", "Pedido não pode ser reembolsado",
					"Só pedido pago, ainda não reembolsado, pode ser reembolsado.");
		}
		int used = tickets.checkedIn(orderId);
		if (used > 0) {
			throw new ApiException(HttpStatus.CONFLICT, "tickets-already-used", "Ingresso já usado",
					used == 1 ? "Um ingresso desse pedido já entrou na festa. Desfaça o check-in antes de reembolsar."
							: used + " ingressos desse pedido já entraram na festa. Desfaça os check-ins antes de reembolsar.");
		}
		PaymentGateway.Seller seller = accounts.seller(organizationId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "seller-not-connected",
					"Conta de recebimento desconectada",
					"Conecte de novo a conta do Mercado Pago que recebeu essa venda para reembolsar."));
		gateway.refund(seller, payment.getProviderPaymentId(), "refund:" + payment.getId());
		payment.refundedHere("manual_refund");
		refunds.record(UuidV7.generate(), payment.getId(), orderId, organizationId, RefundStore.Kind.REFUND,
				payment.getAmountCents(), reason, userId);
		orders.reversed(orderId, false);
		audit.record(organizationId, userId, "order.refunded", "order", orderId,
				Map.of("amountCents", payment.getAmountCents(), "paymentId", payment.getId().toString()));
		return new Refunded(orderId, payment.getAmountCents());
	}

}
