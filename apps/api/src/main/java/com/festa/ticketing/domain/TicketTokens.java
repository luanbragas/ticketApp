package com.festa.ticketing.domain;

import com.festa.shared.security.SecureToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token do QR (SECURITY.md §QR Code, ADR-008): {@code token = base64url(HMAC-SHA256(chave, nonce))}, com nonce
 * aleatório de 32 bytes por ingresso. O banco guarda o nonce e o SHA-256 do token; sem a chave, quem lê o
 * banco não monta um token válido. Com a chave, o sistema remonta o token para o e-mail e "Meus ingressos"
 * sem precisar guardá-lo. Trocar o nonce (transferência, cancelamento) invalida o token antigo.
 */
public final class TicketTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	public TicketTokens(byte[] key) {
		if (key.length != 32) {
			throw new IllegalArgumentException("chave do token precisa ter 32 bytes");
		}
		this.key = new SecretKeySpec(key, "HmacSHA256");
	}

	public static byte[] newNonce() {
		byte[] nonce = new byte[32];
		RANDOM.nextBytes(nonce);
		return nonce;
	}

	/** 43 caracteres base64url, sem dado pessoal nem id sequencial. */
	public String tokenFor(byte[] nonce) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(nonce));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** O que o banco guarda e por onde o token é procurado. */
	public static String hash(String token) {
		return SecureToken.hash(token);
	}

	/** Descarta na hora o que não tem cara de token, sem ir ao banco. */
	public static boolean looksValid(String token) {
		return token != null && token.matches("^[A-Za-z0-9_-]{43}$");
	}

}
