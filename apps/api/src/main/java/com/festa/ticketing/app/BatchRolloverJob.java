package com.festa.ticketing.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Virada por data, a cada minuto (a virada por quantidade acontece na própria venda, no M4). Cada evento
 * vira na sua transação, com os lotes travados: rodar em duas instâncias ao mesmo tempo só repete
 * trabalho, não estraga (ADR-006).
 */
@Component
@ConditionalOnProperty(name = "festa.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BatchRolloverJob {

	private static final Logger log = LoggerFactory.getLogger(BatchRolloverJob.class);

	private final TicketingService ticketing;

	BatchRolloverJob(TicketingService ticketing) {
		this.ticketing = ticketing;
	}

	@Scheduled(fixedDelayString = "${festa.jobs.batch-rollover-delay:60s}", initialDelayString = "10s")
	void run() {
		for (UUID[] due : ticketing.eventsDueForRollover()) {
			try {
				int changed = ticketing.rolloverEvent(due[1], due[0]);
				if (changed > 0) {
					log.info("Virada de lote: evento {} teve {} lote(s) alterado(s)", due[0], changed);
				}
			}
			catch (RuntimeException ex) {
				// Um evento com problema não pode travar a virada dos outros.
				log.error("Falha na virada de lote do evento {}", due[0], ex);
			}
		}
	}

}
