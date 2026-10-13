package com.festa.payment.app;

import com.festa.payment.domain.Payment;
import com.festa.shared.id.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway simulado para desenvolvimento local sem conta no Mercado Pago (ADR-011). Guarda os pagamentos em
 * memória; "pagar" é uma ação da tela de teste que passa pelo mesmo caminho do webhook (consulta aqui e
 * concilia). Recusa subir se a web não estiver em localhost: nunca pode chegar em produção por engano.
 */
@Component
@ConditionalOnProperty(name = "festa.payments.provider", havingValue = "fake")
public class FakePaymentGateway implements PaymentGateway {

	/** Código PIX de mentira, no formato copia e cola, para a tela ter o que mostrar. */
	private static final String FAKE_PIX = "00020126580014br.gov.bcb.pix0136festa-teste-pix-simulado5204000053039865802BR"
			+ "5913FESTA TESTE6009SAO PAULO62070503***6304ABCD";

	private final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();

	FakePaymentGateway(@Value("${festa.web.base-url}") String webBaseUrl) {
		String host = URI.create(webBaseUrl).getHost();
		if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
			throw new IllegalStateException(
				"Gateway simulado só roda com a web em localhost. Em staging/produção use PAYMENTS_PROVIDER=mercadopago.");
		}
	}

	@Override
	public Payment.Provider provider() {
		return Payment.Provider.FAKE;
	}

	@Override
	public GatewayPayment createPix(Seller seller, PixCharge charge) {
		return payments.computeIfAbsent(charge.idempotencyKey(), key -> new GatewayPayment("fake-" + UuidV7.generate(),
				Payment.Status.PENDING, "pending_waiting_transfer", charge.amountCents(), charge.externalReference(),
				FAKE_PIX, null, charge.expiresAt(), "{\"simulated\":true}"));
	}

	@Override
	public GatewayPayment createCard(Seller seller, CardCharge charge) {
		// Token "fake-reject" simula recusa; qualquer outro aprova na hora, como um cartão de teste.
		boolean reject = "fake-reject".equals(charge.cardToken());
		return payments.computeIfAbsent(charge.idempotencyKey(), key -> new GatewayPayment("fake-" + UuidV7.generate(),
				reject ? Payment.Status.REJECTED : Payment.Status.APPROVED,
				reject ? "cc_rejected_insufficient_amount" : "accredited", charge.amountCents(),
				charge.externalReference(), null, null, null, "{\"simulated\":true}"));
	}

	@Override
	public GatewayPayment fetch(Seller seller, String paymentId) {
		return payments.values().stream()
			.filter(p -> p.id().equals(paymentId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("pagamento simulado não existe: " + paymentId));
	}

	@Override
	public void refund(Seller seller, String paymentId, String idempotencyKey) {
		setStatus(paymentId, Payment.Status.REFUNDED, "refunded");
	}

	/** Tela de teste: o comprador "pagou" o PIX. */
	public void approve(String paymentId) {
		setStatus(paymentId, Payment.Status.APPROVED, "accredited");
	}

	@Override
	public String authorizationUrl(String state, String redirectUri) {
		// Sem tela do gateway: volta direto para o retorno com um código de mentira.
		return redirectUri + "?code=fake-" + UuidV7.generate() + "&state=" + state;
	}

	@Override
	public Tokens exchangeCode(String code, String redirectUri) {
		if (code == null || !code.startsWith("fake-")) {
			throw new IllegalArgumentException("código inválido");
		}
		return new Tokens("fake-seller-" + code.substring(5, 13), "fake-access-" + code, "fake-refresh-" + code,
				"fake-public-key", Instant.now().plusSeconds(180L * 24 * 3600));
	}

	@Override
	public Tokens refresh(String refreshToken) {
		return new Tokens("fake-seller", "fake-access-renewed", refreshToken, "fake-public-key",
				Instant.now().plusSeconds(180L * 24 * 3600));
	}

	private void setStatus(String paymentId, Payment.Status status, String detail) {
		payments.replaceAll((key, p) -> p.id().equals(paymentId)
				? new GatewayPayment(p.id(), status, detail, p.amountCents(), p.externalReference(), p.pixQrCode(),
						p.pixQrBase64(), p.pixExpiresAt(), "{\"simulated\":true}")
				: p);
	}

}
