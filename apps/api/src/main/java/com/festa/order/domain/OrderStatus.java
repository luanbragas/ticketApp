package com.festa.order.domain;

/**
 * Ciclo do pedido (ARCHITECTURE.md §Máquinas de estado). No M4 só existem as transições de reserva:
 * {@code PENDING_PAYMENT → EXPIRED}. Pagamento e estorno chegam no M5.
 */
public enum OrderStatus {

	PENDING_PAYMENT, PAID, EXPIRED, FAILED, REFUNDED, PARTIALLY_REFUNDED, CHARGEBACK

}
