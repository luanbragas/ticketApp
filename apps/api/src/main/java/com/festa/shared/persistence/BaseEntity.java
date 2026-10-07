package com.festa.shared.persistence;

import com.festa.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Base das entidades JPA: id UUIDv7 gerado na aplicação e timestamps (docs/DATABASE.md).
 * Implementa {@link Persistable} para o {@code save()} fazer INSERT direto, sem SELECT prévio.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

	@Id
	private UUID id = UuidV7.generate();

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean isNew = true;

	@Override
	public UUID getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PrePersist
	void onPrePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onPreUpdate() {
		updatedAt = Instant.now();
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		isNew = false;
	}

}
