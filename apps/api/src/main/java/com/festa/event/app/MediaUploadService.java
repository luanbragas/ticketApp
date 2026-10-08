package com.festa.event.app;

import com.festa.event.infra.EventLookup;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.id.UuidV7;
import com.festa.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gera a URL pré-assinada para enviar o flyer (SECURITY.md: só JPEG, PNG ou WebP, até 5 MB,
 * nome gerado pelo servidor). O arquivo vai do navegador direto ao bucket; a API só assina.
 */
@Service
public class MediaUploadService {

	public static final long MAX_BYTES = 5L * 1024 * 1024;

	static final Duration TTL = Duration.ofMinutes(5);

	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp");

	public enum Kind {
		FLYER, GALLERY
	}

	public record UploadUrl(URI uploadUrl, String method, Map<String, String> headers, String key, URI publicUrl,
			Instant expiresAt) {
	}

	private final TenantGuard tenantGuard;
	private final EventLookup events;
	private final MediaStorage storage;
	private final Clock clock;

	MediaUploadService(TenantGuard tenantGuard, EventLookup events, MediaStorage storage, Clock clock) {
		this.tenantGuard = tenantGuard;
		this.events = events;
		this.storage = storage;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UploadUrl createUploadUrl(UUID organizationId, UUID eventId, UUID userId, Kind kind, String contentType,
			long size) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN, Role.MANAGER);
		if (!events.existsInOrganization(eventId, organizationId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Evento não encontrado.");
		}
		String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		String extension = EXTENSIONS.get(type);
		if (extension == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported-image", "Formato não aceito",
				"Envie a imagem em JPG, PNG ou WebP.");
		}
		if (size <= 0 || size > MAX_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "image-too-large", "Imagem muito grande",
				"A imagem pode ter até 5 MB.");
		}
		String key = "orgs/%s/events/%s/%s/%s.%s".formatted(organizationId, eventId,
			kind.name().toLowerCase(Locale.ROOT), UuidV7.generate(), extension);
		return new UploadUrl(storage.presignPut(key, type, size, TTL), "PUT", Map.of("Content-Type", type), key,
			storage.publicUrl(key), clock.instant().plus(TTL));
	}

}
