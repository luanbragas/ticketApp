package com.festa.event.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Leitura do evento para outros módulos (que não podem ler a tabela events). */
public interface EventDirectory {

	/** Evento da organização; de outra organização volta vazio. */
	Optional<EventRef> find(UUID organizationId, UUID eventId);

	/** Evento pelo endereço público, só se publicado ou encerrado (rascunho e cancelado ficam escondidos). */
	Optional<EventRef> findPublic(String slug);

	record EventRef(UUID id, UUID organizationId, String slug, EventStatus status, int halfPriceQuotaPercent,
			Integer maxTicketsPerCpf, Instant startsAt, Instant endsAt) {

		/** Dados do evento (e o que pertence a ele, como ingressos) só mudam em rascunho ou publicado. */
		public boolean editable() {
			return status == EventStatus.DRAFT || status == EventStatus.PUBLISHED;
		}

	}

}
