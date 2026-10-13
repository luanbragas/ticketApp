package com.festa.order.domain;

/**
 * Ciclo do pedido (ARCHITECTURE.md §Máquinas de estado). No M4 só existem as transições de reserva:
 * {@code PENDING_PAYMENT → PAID | EXPIRED}, {@code EXPIRED → PAID | REFUNDED} (pagamento atrasado) e
 * {@code PAID → REFUNDED → CHARGEBACK} (reembolso e contestação). Reembolso parcial ainda não existe.
 */
public enum OrderStatus {

	PENDING_PAYMENT, PAID, EXPIRED, FAILED, REFUNDED, PARTIALLY_REFUNDED, CHARGEBACK

}
