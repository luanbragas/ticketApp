package com.festa.ticketing.api;

import java.util.UUID;

/** Ingressos de um pedido para quem decide sobre o pedido inteiro (reembolso). */
public interface OrderTickets {

	/** Quantos ingressos do pedido já entraram na festa. */
	int checkedIn(UUID orderId);

}
