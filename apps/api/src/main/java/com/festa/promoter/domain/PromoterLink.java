package com.festa.promoter.domain;

import com.festa.shared.persistence.BaseEntity;
import com.festa.shared.text.Slugs;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Link de um promoter num evento ({@code /e/{slug}?p={code}}). O código não muda depois de criado: já foi
 * compartilhado no WhatsApp. Desativar faz as compras novas pararem de contar para o promoter.
 */
@Entity
@Table(name = "promoter_event_links")
public class PromoterLink extends BaseEntity {

	public static final int CODE_MAX_LENGTH = 30;
	public static final int CODE_MIN_LENGTH = Slugs.MIN_LENGTH;

	private UUID promoterId;

	private UUID eventId;

	private UUID organizationId;

	private String code;

	private boolean active = true;

	protected PromoterLink() {
	}

	public PromoterLink(Promoter promoter, UUID eventId, String code) {
		this.promoterId = promoter.getId();
		this.organizationId = promoter.getOrganizationId();
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		if (!isValidCode(code)) {
			throw new IllegalArgumentException("código inválido: " + code);
		}
		this.code = code;
	}

	public static boolean isValidCode(String code) {
		return Slugs.isValid(code, CODE_MAX_LENGTH);
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public UUID getPromoterId() {
		return promoterId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getCode() {
		return code;
	}

	public boolean isActive() {
		return active;
	}

}
