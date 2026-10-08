package com.festa.ticketing.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Estoque dos lotes para o módulo de pedidos (ARCHITECTURE.md §Estoque). Postgres é a fonte da verdade:
 * reservar e devolver são {@code UPDATE} condicionais atômicos, sem ler-e-depois-gravar.
 * <p>
 * Os métodos de escrita entram na transação de quem chama: pedido e reserva nascem (ou caem) juntos.
 */
public interface Inventory {

	/** O que o checkout precisa saber do lote. */
	record Offer(UUID batchId, UUID eventId, String batchName, String typeName, boolean halfPrice, long priceCents,
			int maxPerOrder, boolean onSale, int remaining) {
	}

	/** Lotes pedidos que são deste evento (os de outro evento ficam de fora). */
	List<Offer> offers(UUID eventId, Collection<UUID> batchIds);

	/**
	 * Reserva {@code quantity} ingressos se o lote está à venda em {@code now} e tem estoque.
	 *
	 * @return o preço do lote no momento da reserva (o pedido congela este valor), ou vazio se não deu
	 */
	OptionalLong reserve(UUID eventId, UUID batchId, int quantity, Instant now);

	/** Devolve ao lote uma reserva que não virou venda (pedido expirado ou cancelado). */
	void release(UUID batchId, int quantity);

}
