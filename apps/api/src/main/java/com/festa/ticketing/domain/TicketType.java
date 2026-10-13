package com.festa.ticketing.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Tipo de ingresso do evento (Pista, Camarote, Meia...). Preço e estoque ficam nos lotes. */
@Entity
@Table(name = "ticket_types")
public class TicketType extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 60;

	private UUID eventId;

	private UUID organizationId;

	private String name;

	private String description;

	/** Fixo depois de criado: muda a conta da cota de meia de quem já comprou. */
	@Column(name = "is_half_price", updatable = false)
	private boolean halfPrice;

	private short position;

	protected TicketType() {
	}

	public TicketType(UUID eventId, UUID organizationId, String name, String description, boolean halfPrice,
			int position) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.halfPrice = halfPrice;
		this.position = (short) position;
		rename(name);
		describe(description);
	}

	/** {@code null} mantém o valor atual; descrição vazia apaga. */
	public void update(String newName, String newDescription) {
		if (newName != null) {
			rename(newName);
		}
		if (newDescription != null) {
			describe(newDescription);
		}
	}

	private void rename(String newName) {
		String trimmed = Objects.requireNonNull(newName, "name").trim();
		if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("nome inválido");
		}
		name = trimmed;
	}

	private void describe(String newDescription) {
		String trimmed = newDescription == null ? "" : newDescription.trim();
		description = trimmed.isEmpty() ? null : trimmed;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isHalfPrice() {
		return halfPrice;
	}

	public int getPosition() {
		return position;
	}

}
