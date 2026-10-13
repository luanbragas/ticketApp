package com.festa.shared.storage;

import com.festa.shared.id.UuidV7;
import com.festa.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Envio de imagem direto do navegador ao bucket (SECURITY.md §Upload): só JPEG, PNG ou WebP, até 5 MB, nome
 * gerado pelo servidor dentro da pasta que quem chama escolhe. A API só assina; o arquivo não passa por ela.
 */
@Component
public class ImageUploads {

	public static final long MAX_BYTES = 5L * 1024 * 1024;

	static final Duration TTL = Duration.ofMinutes(5);

	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp");

	/** Nome que o servidor gera: UUIDv7 + extensão de imagem aceita. */
	private static final Pattern FILE = Pattern.compile("^[a-z0-9-]+\\.(jpg|png|webp)$");

	public record UploadUrl(URI uploadUrl, String method, Map<String, String> headers, String key, URI publicUrl,
			Instant expiresAt) {
	}

	private final MediaStorage storage;
	private final Clock clock;

	ImageUploads(MediaStorage storage, Clock clock) {
		this.storage = storage;
		this.clock = clock;
	}

	/** @param folder pasta da imagem, terminada em "/" (ex.: {@code orgs/{id}/logo/}) */
	public UploadUrl sign(String folder, String contentType, long size) {
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
		String key = folder + UuidV7.generate() + "." + extension;
		return new UploadUrl(storage.presignPut(key, type, size, TTL), "PUT", Map.of("Content-Type", type), key,
			storage.publicUrl(key), clock.instant().plus(TTL));
	}

	/**
	 * Confere que a chave enviada pelo navegador é um arquivo gerado por {@link #sign} dentro da pasta
	 * esperada (ninguém aponta para arquivo de outra organização) e devolve o endereço público.
	 */
	public URI accept(String folder, String key) {
		if (key == null || !key.startsWith(folder) || !FILE.matcher(key.substring(folder.length())).matches()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-media-key", "Arquivo inválido",
				"Esse arquivo não foi enviado para cá.");
		}
		return storage.publicUrl(key);
	}

}
