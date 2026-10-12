package com.festa.payment.app;

import com.festa.shared.security.SecureToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Assinatura do webhook do Mercado Pago (header {@code x-signature: ts=...,v1=...}): HMAC-SHA256 com a chave
 * secreta do painel sobre {@code id:{data.id};request-id:{x-request-id};ts:{ts};}. Webhook sem assinatura
 * válida é descartado antes de qualquer consulta (SECURITY.md §Pagamentos).
 */
public final class WebhookSignature {

	private WebhookSignature() {
	}

	public static boolean valid(String secret, String signatureHeader, String requestId, String dataId) {
		if (secret == null || secret.isBlank() || signatureHeader == null || dataId == null) {
			return false;
		}
		String ts = null;
		String v1 = null;
		for (String part : signatureHeader.split(",")) {
			String[] pair = part.trim().split("=", 2);
			if (pair.length != 2) {
				continue;
			}
			if (pair[0].trim().equals("ts")) {
				ts = pair[1].trim();
			}
			else if (pair[0].trim().equals("v1")) {
				v1 = pair[1].trim();
			}
		}
		if (ts == null || v1 == null) {
			return false;
		}
		StringBuilder manifest = new StringBuilder().append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
		if (requestId != null && !requestId.isBlank()) {
			manifest.append("request-id:").append(requestId).append(';');
		}
		manifest.append("ts:").append(ts).append(';');
		return SecureToken.constantTimeEquals(hmac(secret, manifest.toString()), v1.toLowerCase(Locale.ROOT));
	}

	static String hmac(String secret, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
