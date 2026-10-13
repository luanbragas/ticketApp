package com.festa.event.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Atração da programação ("A noite"), com horário opcional. */
@Entity
@Table(name = "event_lineup")
public class LineupItem extends BaseEntity {

	private UUID eventId;

	private UUID organizationId;

	private String name;

	private Instant startsAt;

	private short position;

	protected LineupItem() {
	}

	public LineupItem(UUID eventId, UUID organizationId, String name, Instant startsAt, int position) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		String trimmed = Objects.requireNonNull(name, "name").trim();
		if (trimmed.isEmpty() || trimmed.length() > 120) {
			throw new IllegalArgumentException("nome inválido");
		}
		this.name = trimmed;
		this.startsAt = startsAt;
		this.position = (short) position;
	}

	public String getName() {
		return name;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public int getPosition() {
		return position;
	}

}
