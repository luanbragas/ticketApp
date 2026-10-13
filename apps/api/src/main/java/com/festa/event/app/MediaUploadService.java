package com.festa.event.app;

import com.festa.event.infra.EventRepository;
import com.festa.organization.api.Role;
import com.festa.organization.api.TenantGuard;
import com.festa.shared.storage.ImageUploads;
import com.festa.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * URL pré-assinada para enviar o flyer (regras de imagem em {@link ImageUploads}). O arquivo vai do navegador
 * direto ao bucket, na pasta do próprio evento; a API só assina.
 */
@Service
public class MediaUploadService {

	public enum Kind {
		FLYER, GALLERY
	}

	private final TenantGuard tenantGuard;
	private final EventRepository events;
	private final ImageUploads uploads;

	MediaUploadService(TenantGuard tenantGuard, EventRepository events, ImageUploads uploads) {
		this.tenantGuard = tenantGuard;
		this.events = events;
		this.uploads = uploads;
	}

	@Transactional(readOnly = true)
	public ImageUploads.UploadUrl createUploadUrl(UUID organizationId, UUID eventId, UUID userId, Kind kind,
			String contentType, long size) {
		tenantGuard.requireRole(organizationId, userId, Role.OWNER, Role.ADMIN, Role.MANAGER);
		if (!events.existsByIdAndOrganizationId(eventId, organizationId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "Evento não encontrado.");
		}
		return uploads.sign(folder(organizationId, eventId, kind), contentType, size);
	}

	/** Pasta das imagens de um evento: a chave aceita depois precisa estar aqui dentro. */
	public static String folder(UUID organizationId, UUID eventId, Kind kind) {
		return "orgs/%s/events/%s/%s/".formatted(organizationId, eventId, kind.name().toLowerCase(Locale.ROOT));
	}

}
