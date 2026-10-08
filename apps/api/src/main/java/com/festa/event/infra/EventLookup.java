package com.festa.event.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Consultas simples sobre eventos que ainda não pedem entidade JPA. */
@Repository
public class EventLookup {

	private final JdbcClient jdbc;

	EventLookup(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean existsInOrganization(UUID eventId, UUID organizationId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM events WHERE id = ? AND organization_id = ?)")
			.params(eventId, organizationId)
			.query(Boolean.class)
			.single();
	}

}
