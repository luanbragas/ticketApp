package com.festa.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Dado pessoal em repouso (SECURITY.md, CLAUDE.md regra 9): AES-256-GCM para guardar e HMAC-SHA256 para
 * buscar sem decifrar (ex.: limite por CPF). As duas chaves vêm de variáveis de ambiente.
 * <p>
 * Formato guardado: {@code [versão 1 byte][IV 12 bytes][cifrado + tag 16 bytes]}. A versão permite trocar
 * a chave no futuro sem perder o que já foi gravado.
 */
@Component
public class PersonalDataCipher {

	private static final byte VERSION = 1;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec encryptionKey;
	private final SecretKeySpec hashKey;
	private final SecureRandom random = new SecureRandom();

	PersonalDataCipher(@Value("${festa.crypto.personal-data-key}") String encryptionKey,
			@Value("${festa.crypto.personal-data-hash-key}") String hashKey) {
		this.encryptionKey = new SecretKeySpec(key(encryptionKey, "festa.crypto.personal-data-key"), "AES");
		this.hashKey = new SecretKeySpec(key(hashKey, "festa.crypto.personal-data-hash-key"), "HmacSHA256");
	}

	public byte[] encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(VERSION).put(iv).put(sealed).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("falha ao cifrar", ex);
		}
	}

	public String decrypt(byte[] stored) {
		if (stored == null || stored.length < 1 + IV_BYTES || stored[0] != VERSION) {
			throw new IllegalArgumentException("formato cifrado desconhecido");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
			byte[] plain = cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("falha ao decifrar", ex);
		}
	}

	/** Mesmo valor sempre dá o mesmo hash (para buscar); sem a chave, não dá para testar CPFs. */
	public String hash(String plain) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(hashKey);
			return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("falha no hash", ex);
		}
	}

	private static byte[] key(String base64, String property) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(property + " precisa ser base64", ex);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException(property + " precisa ter 32 bytes (base64 de 256 bits)");
		}
		return bytes;
	}

}
