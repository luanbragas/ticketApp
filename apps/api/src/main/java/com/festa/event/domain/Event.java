package com.festa.event.domain;

import com.festa.event.api.EventStatus;
import com.festa.shared.persistence.BaseEntity;
import com.festa.shared.text.Slugs;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Evento de uma organização. Status só muda pelos métodos de transição; dados só podem ser
 * editados em rascunho ou publicado (docs/ARCHITECTURE.md §Máquinas de estado).
 */
@Entity
@Table(name = "events")
public class Event extends BaseEntity {

	public static final int SLUG_MAX_LENGTH = 80;
	public static final int NAME_MAX_LENGTH = 120;

	private static final Pattern ACCENT = Pattern.compile("^#[0-9a-f]{6}$");

	private UUID organizationId;

	private String slug;

	private String name;

	private String description;

	private String category;

	private Instant startsAt;

	private Instant endsAt;

	private String venueName;

	private String address;

	private String city;

	private short minAge = 18;

	private boolean hasOpenBar;

	private short halfPriceQuotaPercent = 40;

	private Short maxTicketsPerCpf;

	private String accentColor;

	@Enumerated(EnumType.STRING)
	private EventStatus status = EventStatus.DRAFT;

	private Instant publishedAt;

	protected Event() {
	}

	/** Novo evento em rascunho: só nome e slug são obrigatórios. */
	public Event(UUID organizationId, String name, String slug) {
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		if (!Slugs.isValid(slug, SLUG_MAX_LENGTH)) {
			throw new IllegalArgumentException("slug inválido: " + slug);
		}
		this.slug = slug;
		rename(name);
	}

	/** Valores novos dos dados do evento; {@code null} mantém o atual. Texto vazio apaga campo opcional. */
	public record Changes(String name, String description, String category, Instant startsAt, Instant endsAt,
			String venueName, String address, String city, Integer minAge, Boolean hasOpenBar,
			Integer halfPriceQuotaPercent, Integer maxTicketsPerCpf, String accentColor) {
	}

	public void update(Changes changes) {
		requireEditable();
		if (changes.name() != null) {
			rename(changes.name());
		}
		description = optional(changes.description(), description);
		category = optional(changes.category(), category);
		venueName = optional(changes.venueName(), venueName);
		address = optional(changes.address(), address);
		city = optional(changes.city(), city);
		Instant newStart = changes.startsAt() != null ? changes.startsAt() : startsAt;
		Instant newEnd = changes.endsAt() != null ? changes.endsAt() : endsAt;
		if (newStart != null && newEnd != null && !newEnd.isAfter(newStart)) {
			throw EventRuleException.rule("invalid-dates", "O fim precisa ser depois do início.");
		}
		startsAt = newStart;
		endsAt = newEnd;
		int newMinAge = changes.minAge() != null ? changes.minAge() : minAge;
		boolean newOpenBar = changes.hasOpenBar() != null ? changes.hasOpenBar() : hasOpenBar;
		if (newOpenBar && newMinAge < 18) {
			throw EventRuleException.rule("open-bar-needs-18", "Evento com open bar precisa ser para maiores de 18.");
		}
		minAge = (short) newMinAge;
		hasOpenBar = newOpenBar;
		if (changes.halfPriceQuotaPercent() != null) {
			halfPriceQuotaPercent = changes.halfPriceQuotaPercent().shortValue();
		}
		if (changes.maxTicketsPerCpf() != null) {
			maxTicketsPerCpf = changes.maxTicketsPerCpf().shortValue();
		}
		if (changes.accentColor() != null) {
			String color = changes.accentColor().isBlank() ? null : changes.accentColor();
			if (color != null && !ACCENT.matcher(color).matches()) {
				throw new IllegalArgumentException("cor inválida: " + color);
			}
			accentColor = color;
		}
		if (status == EventStatus.PUBLISHED && venueName == null) {
			throw EventRuleException.rule("venue-required", "Evento publicado precisa de local.");
		}
	}

	/** Rascunho → publicado. Exige data, local, início no futuro e flyer. */
	public void publish(Instant now, boolean hasFlyer) {
		publish(now, hasFlyer, List.of());
	}

	/**
	 * Como {@link #publish(Instant, boolean)}, somando o que outros módulos dizem que falta
	 * (ex.: {@code tickets}).
	 */
	public void publish(Instant now, boolean hasFlyer, List<String> missingElsewhere) {
		if (status != EventStatus.DRAFT) {
			throw EventRuleException.invalidTransition(status, "publicar");
		}
		List<String> missing = new ArrayList<>();
		if (startsAt == null) {
			missing.add("startsAt");
		}
		if (endsAt == null) {
			missing.add("endsAt");
		}
		if (venueName == null) {
			missing.add("venueName");
		}
		if (!hasFlyer) {
			missing.add("flyer");
		}
		missing.addAll(missingElsewhere);
		if (!missing.isEmpty()) {
			throw EventRuleException.notReady(missing);
		}
		if (!startsAt.isAfter(now)) {
			throw EventRuleException.rule("starts-in-the-past", "A data de início já passou.");
		}
		status = EventStatus.PUBLISHED;
		publishedAt = now;
	}

	/** Publicado → encerrado (manual; o encerramento automático vem depois). */
	public void end() {
		if (status != EventStatus.PUBLISHED) {
			throw EventRuleException.invalidTransition(status, "encerrar");
		}
		status = EventStatus.ENDED;
	}

	/** Rascunho ou publicado → cancelado. */
	public void cancel() {
		if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) {
			throw EventRuleException.invalidTransition(status, "cancelar");
		}
		status = EventStatus.CANCELLED;
	}

	public void requireEditable() {
		if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) {
			throw EventRuleException.invalidTransition(status, "editar");
		}
	}

	private void rename(String newName) {
		Objects.requireNonNull(newName, "name");
		String trimmed = newName.trim();
		if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("nome inválido");
		}
		name = trimmed;
	}

	private static String optional(String value, String current) {
		if (value == null) {
			return current;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getCategory() {
		return category;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public String getVenueName() {
		return venueName;
	}

	public String getAddress() {
		return address;
	}

	public String getCity() {
		return city;
	}

	public int getMinAge() {
		return minAge;
	}

	public boolean isHasOpenBar() {
		return hasOpenBar;
	}

	public int getHalfPriceQuotaPercent() {
		return halfPriceQuotaPercent;
	}

	public Integer getMaxTicketsPerCpf() {
		return maxTicketsPerCpf == null ? null : maxTicketsPerCpf.intValue();
	}

	public String getAccentColor() {
		return accentColor;
	}

	public EventStatus getStatus() {
		return status;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

}
