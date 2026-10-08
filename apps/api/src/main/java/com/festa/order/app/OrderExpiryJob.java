package com.festa.order.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Roda a expiração a cada {@code festa.orders.expiry-job-delay}; pedido de 10 min expira em até 10,5 min. */
@Component
@ConditionalOnProperty(name = "festa.jobs.enabled", havingValue = "true", matchIfMissing = true)
class OrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

	private final OrderExpiryService expiry;

	OrderExpiryJob(OrderExpiryService expiry) {
		this.expiry = expiry;
	}

	@Scheduled(fixedDelayString = "${festa.orders.expiry-job-delay}", initialDelayString = "15s")
	void run() {
		try {
			int expired;
			do {
				expired = expiry.expireDue();
				if (expired > 0) {
					log.info("Pedidos expirados: {}", expired);
				}
			}
			while (expired == OrderExpiryService.BATCH_SIZE);
		}
		catch (RuntimeException ex) {
			log.error("Falha ao expirar pedidos", ex);
		}
	}

}
