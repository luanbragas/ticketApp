package com.festa.event.app;

import java.net.URI;
import java.time.Duration;

/** Armazenamento de imagens dos eventos (Cloudflare R2 em produção, S3Mock em dev). */
public interface MediaStorage {

	/**
	 * URL pré-assinada para o navegador enviar o arquivo direto ao bucket com {@code PUT}.
	 * Tipo e tamanho entram na assinatura: um envio com outro {@code Content-Type} ou outro
	 * {@code Content-Length} é recusado pelo próprio bucket.
	 */
	URI presignPut(String key, String contentType, long contentLength, Duration ttl);

	/** Endereço público (CDN) do objeto depois de enviado. */
	URI publicUrl(String key);

}
