package com.festa.shared.outbox;

import com.festa.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entrega do outbox: só com transação, idempotência de quem consome, tentativas e desistência. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, OutboxWorkerTest.Handlers.class })
class OutboxWorkerTest {

	@Autowired
	OutboxPublisher publisher;

	@Autowired
	OutboxWorker worker;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	Handlers handlers;

	@Test
	void deliversOnceAndMarksDone() {
		UUID aggregate = UUID.randomUUID();
		tx.executeWithoutResult(s -> publisher.publish("TestPing", aggregate, Map.of("hello", "festa")));

		worker.drain(100);
		worker.drain(100);

		assertThat(handlers.pings).containsOnlyOnce(aggregate);
		assertThat(status(aggregate)).isEqualTo("DONE");
	}

	@Test
	void publishingOutsideATransactionIsRefused() {
		assertThatThrownBy(() -> publisher.publish("TestPing", UUID.randomUUID(), Map.of()))
			.hasMessageContaining("transaction");
	}

	@Test
	void failureWaitsAndRetriesThenGivesUp() {
		UUID aggregate = UUID.randomUUID();
		tx.executeWithoutResult(s -> publisher.publish("TestBoom", aggregate, Map.of()));

		worker.drain(100);
		assertThat(status(aggregate)).isEqualTo("PENDING");
		assertThat(jdbc.sql("SELECT attempts FROM outbox_events WHERE aggregate_id = ?").param(aggregate)
			.query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT last_error FROM outbox_events WHERE aggregate_id = ?").param(aggregate)
			.query(String.class).single()).contains("boom");

		// Ainda esperando a próxima tentativa: não roda de novo agora.
		int before = handlers.booms.get();
		worker.drain(100);
		assertThat(handlers.booms.get()).isEqualTo(before);

		for (int i = 1; i < OutboxWorker.MAX_ATTEMPTS; i++) {
			jdbc.sql("UPDATE outbox_events SET next_attempt_at = now() WHERE aggregate_id = ?").param(aggregate).update();
			worker.drain(100);
		}
		assertThat(status(aggregate)).isEqualTo("FAILED");
	}

	private String status(UUID aggregate) {
		return jdbc.sql("SELECT status FROM outbox_events WHERE aggregate_id = ?").param(aggregate)
			.query(String.class).single();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Handlers {

		final List<UUID> pings = new CopyOnWriteArrayList<>();

		final AtomicInteger booms = new AtomicInteger();

		@Bean
		OutboxHandler pingHandler(Handlers handlers) {
			return new OutboxHandler() {
				@Override
				public String type() {
					return "TestPing";
				}

				@Override
				public void handle(OutboxMessage message) {
					handlers.pings.add(message.aggregateId());
				}
			};
		}

		@Bean
		OutboxHandler boomHandler(Handlers handlers) {
			return new OutboxHandler() {
				@Override
				public String type() {
					return "TestBoom";
				}

				@Override
				public void handle(OutboxMessage message) {
					handlers.booms.incrementAndGet();
					throw new IllegalStateException("boom");
				}
			};
		}

	}

}
