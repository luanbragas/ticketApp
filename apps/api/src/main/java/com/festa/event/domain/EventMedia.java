package com.festa.event.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Imagem do evento. Hoje só o flyer (um por evento); a galeria vem depois. */
@Entity
@Table(name = "event_media")
public class EventMedia extends BaseEntity {

	public enum Kind {
		FLYER, GALLERY
	}

	private UUID eventId;

	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	private Kind kind;

	private String url;

	private Integer width;

	private Integer height;

	private short position;

	protected EventMedia() {
	}

	public EventMedia(UUID eventId, UUID organizationId, Kind kind, String url, int width, int height) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.kind = Objects.requireNonNull(kind, "kind");
		replace(url, width, height);
	}

	/** Troca a imagem mantendo a linha (o flyer é único por evento). */
	public void replace(String newUrl, int newWidth, int newHeight) {
		if (newWidth <= 0 || newHeight <= 0) {
			throw new IllegalArgumentException("dimensões inválidas");
		}
		this.url = Objects.requireNonNull(newUrl, "url");
		this.width = newWidth;
		this.height = newHeight;
	}

	public UUID getEventId() {
		return eventId;
	}

	public Kind getKind() {
		return kind;
	}

	public String getUrl() {
		return url;
	}

	public Integer getWidth() {
		return width;
	}

	public Integer getHeight() {
		return height;
	}

}
