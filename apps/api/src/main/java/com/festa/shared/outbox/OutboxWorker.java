package com.festa.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entrega os eventos do outbox aos handlers (ARCHITECTURE.md §Outbox). Cada evento é processado na sua
 * transação, travado com {@code FOR UPDATE SKIP LOCKED}: duas instâncias nunca pegam o mesmo evento.
 * Falha tenta de novo com espera crescente; depois de {@link #MAX_ATTEMPTS}, o evento fica {@code FAILED}
 * para análise.
 */
@Component
public class OutboxWorker {

	static final int MAX_ATTEMPTS = 10;

	private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

	private final JdbcClient jdbc;
	private final Map<String, OutboxHandler> handlers;
	private final TransactionTemplate tx;
	private final TransactionTemplate failureTx;
	private final Clock clock;

	OutboxWorker(JdbcClient jdbc, List<OutboxHandler> handlers, PlatformTransactionManager transactions, Clock clock) {
		this.jdbc = jdbc;
		this.handlers = handlers.stream().collect(Collectors.toMap(OutboxHandler::type, Function.identity()));
		this.tx = new TransactionTemplate(transactions);
		this.failureTx = new TransactionTemplate(transactions);
		this.failureTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	/**
	 * Processa os eventos vencidos, um por transação, até não sobrar nenhum ou chegar a {@code limit}.
	 *
	 * @return quantos eventos foram entregues com sucesso
	 */
	public int drain(int limit) {
		int delivered = 0;
		for (int i = 0; i < limit; i++) {
			Outcome outcome = processNext();
			if (outcome == Outcome.NOTHING) {
				break;
			}
			if (outcome == Outcome.DELIVERED) {
				delivered++;
			}
		}
		return delivered;
	}

	private enum Outcome {
		NOTHING, DELIVERED, FAILED
	}

	private record Failure(OutboxMessage message, RuntimeException error) {
	}

	private Outcome processNext() {
		Instant now = clock.instant();
		Failure[] failure = new Failure[1];
		Outcome outcome = tx.execute(status -> {
			Optional<OutboxMessage> next = jdbc.sql("""
					SELECT id, type, aggregate_id, payload::text AS payload, attempts FROM outbox_events
					 WHERE status = 'PENDING' AND next_attempt_at <= :now
					 ORDER BY next_attempt_at
					 LIMIT 1
					 FOR UPDATE SKIP LOCKED
					""")
				.param("now", Timestamp.from(now))
				.query((rs, n) -> new OutboxMessage(rs.getObject("id", UUID.class), rs.getString("type"),
						rs.getObject("aggregate_id", UUID.class), rs.getString("payload"), rs.getInt("attempts")))
				.optional();
			if (next.isEmpty()) {
				return Outcome.NOTHING;
			}
			OutboxMessage message = next.get();
			try {
				OutboxHandler handler = handlers.get(message.type());
				if (handler == null) {
					throw new IllegalStateException("nenhum handler para " + message.type());
				}
				handler.handle(message);
				jdbc.sql("UPDATE outbox_events SET status = 'DONE', attempts = attempts + 1, processed_at = :now WHERE id = :id")
					.param("now", Timestamp.from(now))
					.param("id", message.id())
					.update();
				return Outcome.DELIVERED;
			}
			catch (RuntimeException ex) {
				// Desfaz o que o handler gravou; a falha é registrada numa transação à parte.
				status.setRollbackOnly();
				failure[0] = new Failure(message, ex);
				return Outcome.FAILED;
			}
		});
		if (failure[0] != null) {
			recordFailure(failure[0].message(), failure[0].error(), now);
		}
		return outcome;
	}

	private void recordFailure(OutboxMessage message, RuntimeException ex, Instant now) {
		int attempts = message.attempts() + 1;
		boolean giveUp = attempts >= MAX_ATTEMPTS;
		// 30 s, 1 min, 2 min... até 1 h.
		Duration wait = Duration.ofSeconds(Math.min(3600, 30L * (1L << Math.min(attempts - 1, 7))));
		log.warn("Evento {} ({}) falhou na tentativa {}: {}", message.id(), message.type(), attempts, ex.toString());
		failureTx.executeWithoutResult(status -> jdbc.sql("""
				UPDATE outbox_events
				   SET attempts = :attempts, last_error = :error, next_attempt_at = :next,
				       status = CASE WHEN :giveUp THEN 'FAILED' ELSE 'PENDING' END
				 WHERE id = :id
				""")
			.param("attempts", attempts)
			.param("error", truncate(ex.toString()))
			.param("next", Timestamp.from(now.plus(wait)))
			.param("giveUp", giveUp)
			.param("id", message.id())
			.update());
	}

	private static String truncate(String text) {
		return text.length() <= 1000 ? text : text.substring(0, 1000);
	}

}
