package com.festa.payment.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Assinatura do webhook: só o aviso assinado com a chave do painel passa (SECURITY.md checklist). */
class WebhookSignatureTest {

	private static final String SECRET = "segredo";

	@Test
	void acceptsTheExactManifest() {
		String v1 = WebhookSignature.hmac(SECRET, "id:123456;request-id:req-1;ts:1700000000;");

		assertThat(WebhookSignature.valid(SECRET, "ts=1700000000,v1=" + v1, "req-1", "123456")).isTrue();
		assertThat(WebhookSignature.valid(SECRET, " ts=1700000000 , v1=" + v1.toUpperCase(), "req-1", "123456")).isTrue();
	}

	@Test
	void rejectsAnythingElse() {
		String v1 = WebhookSignature.hmac(SECRET, "id:123456;request-id:req-1;ts:1700000000;");

		assertThat(WebhookSignature.valid(SECRET, "ts=1700000000,v1=" + v1, "req-1", "999999")).isFalse();
		assertThat(WebhookSignature.valid(SECRET, "ts=1700000001,v1=" + v1, "req-1", "123456")).isFalse();
		assertThat(WebhookSignature.valid("outro", "ts=1700000000,v1=" + v1, "req-1", "123456")).isFalse();
		assertThat(WebhookSignature.valid(SECRET, null, "req-1", "123456")).isFalse();
		assertThat(WebhookSignature.valid("", "ts=1700000000,v1=" + v1, "req-1", "123456")).isFalse();
		assertThat(WebhookSignature.valid(SECRET, "v1=" + v1, "req-1", "123456")).isFalse();
	}

	@Test
	void mercadoPagoAmountsAreCents() {
		assertThat(MercadoPagoGateway.reais(4950)).hasToString("49.50");
		assertThat(MercadoPagoGateway.cents(new java.math.BigDecimal("49.5"))).isEqualTo(4950);
		assertThat(MercadoPagoGateway.status("in_process")).isEqualTo(com.festa.payment.domain.Payment.Status.PENDING);
		assertThat(MercadoPagoGateway.status("approved")).isEqualTo(com.festa.payment.domain.Payment.Status.APPROVED);
	}

}
