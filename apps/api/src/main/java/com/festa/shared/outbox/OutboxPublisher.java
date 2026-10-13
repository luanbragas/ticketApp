package com.festa.shared.outbox;

import com.festa.shared.id.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Grava um evento de domínio no outbox. Exige transação aberta: o evento só existe se a mudança de
 * estado que o gerou for gravada junto.
 */
@Component
public class OutboxPublisher {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock clock;

	OutboxPublisher(JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public UUID publish(String type, UUID aggregateId, Object payload) {
		UUID id = UuidV7.generate();
		jdbc.sql("""
				INSERT INTO outbox_events (id, type, aggregate_id, payload, next_attempt_at)
				VALUES (:id, :type, :aggregateId, CAST(:payload AS jsonb), :now)
				""")
			// Mesmo relógio do worker: o relógio do banco pode estar uns milissegundos à frente.
			.param("now", Timestamp.from(clock.instant()))
			.param("id", id)
			.param("type", type)
			.param("aggregateId", aggregateId)
			.param("payload", json.writeValueAsString(payload))
			.update();
		return id;
	}

}
