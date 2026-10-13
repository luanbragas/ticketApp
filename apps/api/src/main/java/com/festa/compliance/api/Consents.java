package com.festa.compliance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Termos de uso e política de privacidade versionados e o registro de aceite (SECURITY.md §LGPD). Grava na
 * transação de quem chama: se o cadastro ou o pedido desfizer, o aceite some junto.
 */
public interface Consents {

	enum Purpose {
		SIGNUP, CHECKOUT
	}

	/** De onde veio o aceite (prova). */
	record Origin(String ip, String userAgent) {
	}

	/** Texto vigente: {@code kind} é TERMS ou PRIVACY; {@code url} é o caminho da página na web. */
	record Version(String kind, String version, String url, Instant publishedAt) {
	}

	/** Versões vigentes de termos e privacidade. */
	List<Version> current();

	/**
	 * Registra o aceite das versões vigentes de termos e privacidade.
	 *
	 * @param userId nulo quando quem aceita ainda não tem conta (comprador no checkout)
	 * @param referenceId o pedido, no checkout
	 * @return a versão dos termos aceita
	 */
	String accept(UUID userId, String email, Purpose purpose, UUID referenceId, Origin origin);

}
