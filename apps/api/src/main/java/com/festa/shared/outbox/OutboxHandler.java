package com.festa.shared.outbox;

/**
 * Consumidor de um tipo de evento do outbox. Precisa ser idempotente: o mesmo evento pode chegar duas
 * vezes (ARCHITECTURE.md §Outbox). Roda dentro da transação que marca o evento como entregue.
 */
public interface OutboxHandler {

	/** Tipo de evento que este handler trata (ex.: {@code OrderPaid}). */
	String type();

	void handle(OutboxMessage message);

}
