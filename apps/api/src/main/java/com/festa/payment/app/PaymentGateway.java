package com.festa.payment.app;

import com.festa.payment.domain.Payment;

import java.time.Instant;

/**
 * Porta para o gateway (ADR-011). Valores em centavos aqui; cada adaptador converte para o formato do
 * gateway. Implementações: Mercado Pago (produção) e simulado (só desenvolvimento local).
 */
public interface PaymentGateway {

	Payment.Provider provider();

	/** Credencial do vendedor (produtor) com que o pagamento é criado e consultado. */
	record Seller(String accessToken) {
	}

	record Payer(String name, String email) {
	}

	/**
	 * @param platformFeeCents taxa retida pela plataforma no split ({@code application_fee})
	 * @param externalReference id do pedido: a conciliação confere que o pagamento é deste pedido
	 */
	record PixCharge(String idempotencyKey, String externalReference, long amountCents, long platformFeeCents,
			String description, Payer payer, Instant expiresAt, String notificationUrl) {
	}

	/** Cartão tokenizado no navegador pelo SDK do gateway; o servidor nunca vê o número. */
	record CardCharge(String idempotencyKey, String externalReference, long amountCents, long platformFeeCents,
			String description, Payer payer, String cardToken, String paymentMethodId, String issuerId,
			String notificationUrl) {
	}

	/** Retrato do pagamento no gateway. {@code raw} vai para auditoria (sem dado de cartão). */
	record GatewayPayment(String id, Payment.Status status, String statusDetail, long amountCents,
			String externalReference, String pixQrCode, String pixQrBase64, Instant pixExpiresAt, String raw) {
	}

	/** Resultado do OAuth: credencial do vendedor. */
	record Tokens(String userId, String accessToken, String refreshToken, String publicKey, Instant expiresAt) {
	}

	GatewayPayment createPix(Seller seller, PixCharge charge);

	GatewayPayment createCard(Seller seller, CardCharge charge);

	GatewayPayment fetch(Seller seller, String paymentId);

	void refund(Seller seller, String paymentId, String idempotencyKey);

	/** Para onde mandar o produtor autorizar a plataforma. */
	String authorizationUrl(String state, String redirectUri);

	Tokens exchangeCode(String code, String redirectUri);

	Tokens refresh(String refreshToken);

}
