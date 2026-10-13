package com.festa.event.api;

import java.util.List;
import java.util.UUID;

/**
 * O que outro módulo exige antes de o evento ser publicado (ex.: ticketing exige ingressos). Assim o
 * módulo event não depende de quem tem a regra: cada módulo registra a sua como bean.
 */
public interface PublishPrerequisite {

	/** Itens que faltam, com os nomes usados no campo {@code missing} da API; vazio = pronto. */
	List<String> missingFor(UUID organizationId, UUID eventId);

}
