package com.festa.event.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Encerramento automático (ARCHITECTURE.md §Máquinas de estado): evento publicado vira {@code ENDED} quando passa
 * do horário de término. A venda já para no término (o checkout confere); aqui é o status para listas e painel.
 */
@Service
public class EventEnding {

	private final JdbcClient jdbc;
	private final Clock clock;

	EventEnding(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** @return quantos eventos foram encerrados agora */
	@Transactional
	public int endFinished() {
		return jdbc.sql("""
				UPDATE events SET status = 'ENDED', updated_at = now()
				 WHERE status = 'PUBLISHED' AND ends_at <= :now
				""")
			.param("now", Timestamp.from(clock.instant()))
			.update();
	}

	@Component
	@ConditionalOnProperty(name = "festa.jobs.enabled", havingValue = "true", matchIfMissing = true)
	static class Job {

		private static final Logger log = LoggerFactory.getLogger(Job.class);

		private final EventEnding ending;

		Job(EventEnding ending) {
			this.ending = ending;
		}

		@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
		void run() {
			try {
				int ended = ending.endFinished();
				if (ended > 0) {
					log.info("Eventos encerrados: {}", ended);
				}
			}
			catch (RuntimeException ex) {
				log.error("Falha ao encerrar eventos", ex);
			}
		}

	}

}
