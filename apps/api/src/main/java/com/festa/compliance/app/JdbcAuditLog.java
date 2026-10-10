package com.festa.compliance.app;

import com.festa.compliance.api.AuditLog;
import com.festa.shared.id.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
class JdbcAuditLog implements AuditLog {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock clock;

	JdbcAuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID organizationId, UUID actorUserId, String action, String entity, UUID entityId,
			Map<String, Object> data) {
		jdbc.sql("""
				INSERT INTO audit_logs (id, organization_id, actor_user_id, action, entity, entity_id, data, created_at)
				VALUES (:id, :organizationId, :actor, :action, :entity, :entityId, CAST(:data AS jsonb), :at)
				""")
			.param("id", UuidV7.generate())
			.param("organizationId", organizationId)
			.param("actor", actorUserId)
			.param("action", action)
			.param("entity", entity)
			.param("entityId", entityId)
			.param("data", json.writeValueAsString(data == null ? Map.of() : data))
			.param("at", Timestamp.from(clock.instant()))
			.update();
	}

}
