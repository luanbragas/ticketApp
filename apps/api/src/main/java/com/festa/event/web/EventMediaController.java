package com.festa.event.web;

import com.festa.event.app.MediaUploadService;
import com.festa.event.app.MediaUploadService.Kind;
import com.festa.event.app.MediaUploadService.UploadUrl;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/events/{eventId}/media")
class EventMediaController {

	private final MediaUploadService uploads;

	EventMediaController(MediaUploadService uploads) {
		this.uploads = uploads;
	}

	record UploadUrlRequest(
			@NotNull(message = "Informe o tipo de mídia.") Kind kind,
			@NotNull(message = "Informe o formato da imagem.")
			@Pattern(regexp = "image/(jpeg|png|webp)", message = "Envie a imagem em JPG, PNG ou WebP.") String contentType,
			@Min(value = 1, message = "Arquivo vazio.")
			@Max(value = MediaUploadService.MAX_BYTES, message = "A imagem pode ter até 5 MB.") long size) {
	}

	/** Passo 1 do envio: a web recebe a URL, faz o PUT direto no bucket e depois salva a mídia no evento. */
	@PostMapping("/upload-url")
	UploadUrl uploadUrl(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID eventId, @Valid @RequestBody UploadUrlRequest body) {
		return uploads.createUploadUrl(orgId, eventId, user.id(), body.kind(), body.contentType(), body.size());
	}

}
