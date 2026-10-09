package com.festa.promoter.api;

import java.util.Optional;
import java.util.UUID;

/** Atribuição de venda para o módulo de pedidos (ADR-009). */
public interface PromoterLinks {

	/**
	 * Promoter dono do código no evento, se o link existe e está ativo. Código desconhecido não é erro: a
	 * compra segue sem atribuição.
	 */
	Optional<UUID> resolve(UUID eventId, String code);

}
