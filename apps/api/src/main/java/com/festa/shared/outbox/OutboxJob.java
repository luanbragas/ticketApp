package com.festa.shared.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Entrega o outbox a cada 2 s: ingresso e e-mail saem poucos segundos depois do pagamento. */
@Component
@ConditionalOnProperty(name = "festa.jobs.enabled", havingValue = "true", matchIfMissing = true)
class OutboxJob {

	private final OutboxWorker worker;

	OutboxJob(OutboxWorker worker) {
		this.worker = worker;
	}

	@Scheduled(fixedDelayString = "${festa.outbox.poll-delay:2s}", initialDelayString = "5s")
	void run() {
		worker.drain(100);
	}

}
