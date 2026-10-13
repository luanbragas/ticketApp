package com.festa.checkin.infra;

import com.festa.shared.id.UuidV7;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Histórico de leituras na portaria ({@code checkins}). Só uma linha ativa por ingresso. */
@Repository
public class CheckinStore {

	private final JdbcClient jdbc;

	CheckinStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public enum Source {
		ONLINE, OFFLINE
	}

	/** Check-in que vale para o ingresso (não desfeito, não duplicado). */
	public record Active(UUID id, UUID ticketId, Instant checkedInAt) {
	}

	public record Row(UUID id, UUID ticketId, UUID eventId, UUID organizationId, boolean active) {
	}

	public UUID insert(UUID ticketId, UUID eventId, UUID organizationId, UUID operator, Instant at, Source source,
			String deviceId, boolean duplicate) {
		UUID id = UuidV7.generate();
		jdbc.sql("""
				INSERT INTO checkins (id, ticket_id, event_id, organization_id, operator_user_id, checked_in_at, source,
				                      device_id, duplicate)
				VALUES (:id, :ticketId, :eventId, :organizationId, :operator, :at, :source, :deviceId, :duplicate)
				""")
			.param("id", id)
			.param("ticketId", ticketId)
			.param("eventId", eventId)
			.param("organizationId", organizationId)
			.param("operator", operator)
			.param("at", Timestamp.from(at))
			.param("source", source.name())
			.param("deviceId", deviceId)
			.param("duplicate", duplicate)
			.update();
		return id;
	}

	public Optional<Active> active(UUID ticketId) {
		return jdbc.sql("""
				SELECT id, ticket_id, checked_in_at FROM checkins
				 WHERE ticket_id = :ticketId AND undone_at IS NULL AND NOT duplicate
				 FOR UPDATE
				""")
			.param("ticketId", ticketId)
			.query((rs, i) -> new Active(rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class),
					rs.getTimestamp("checked_in_at").toInstant()))
			.optional();
	}

	public Map<UUID, Active> activeFor(UUID eventId, Collection<UUID> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				SELECT id, ticket_id, checked_in_at FROM checkins
				 WHERE event_id = :eventId AND ticket_id IN (:ids) AND undone_at IS NULL AND NOT duplicate
				""")
			.param("eventId", eventId)
			.param("ids", ticketIds)
			.query((rs, i) -> new Active(rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class),
					rs.getTimestamp("checked_in_at").toInstant()))
			.list()
			.stream()
			.collect(Collectors.toMap(Active::ticketId, a -> a));
	}

	/** A mesma leitura reenviada pelo aparelho (sincronização repetida) não vira outra linha. */
	public boolean alreadySynced(UUID ticketId, String deviceId, Instant at) {
		return jdbc.sql("""
				SELECT count(*) FROM checkins
				 WHERE ticket_id = :ticketId AND device_id = :deviceId AND checked_in_at = :at
				""")
			.param("ticketId", ticketId)
			.param("deviceId", deviceId)
			.param("at", Timestamp.from(at))
			.query(Integer.class)
			.single() > 0;
	}

	/** Leitura que perdeu para outra mais antiga. */
	public void markDuplicate(UUID checkinId) {
		jdbc.sql("UPDATE checkins SET duplicate = true WHERE id = :id").param("id", checkinId).update();
	}

	public Optional<Row> lock(UUID checkinId, UUID organizationId) {
		return jdbc.sql("""
				SELECT id, ticket_id, event_id, organization_id, (undone_at IS NULL AND NOT duplicate) AS active
				  FROM checkins WHERE id = :id AND organization_id = :organizationId
				 FOR UPDATE
				""")
			.param("id", checkinId)
			.param("organizationId", organizationId)
			.query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class),
					rs.getObject("event_id", UUID.class), rs.getObject("organization_id", UUID.class),
					rs.getBoolean("active")))
			.optional();
	}

	public void undo(UUID checkinId, UUID by, Instant at) {
		jdbc.sql("UPDATE checkins SET undone_at = :at, undone_by = :by WHERE id = :id")
			.param("at", Timestamp.from(at))
			.param("by", by)
			.param("id", checkinId)
			.update();
	}

	/** Leituras duplicadas do evento (relatório de conflitos do modo offline). */
	public int duplicates(UUID eventId) {
		return jdbc.sql("SELECT count(*) FROM checkins WHERE event_id = :eventId AND duplicate AND undone_at IS NULL")
			.param("eventId", eventId)
			.query(Integer.class)
			.single();
	}

}
