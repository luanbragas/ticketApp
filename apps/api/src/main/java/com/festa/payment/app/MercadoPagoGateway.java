package com.festa.payment.app;

import com.festa.payment.domain.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado Pago, modelo marketplace (ADR-003, ADR-011): o pagamento é criado com o token OAuth do produtor em
 * {@code /v1/payments} com {@code application_fee} = taxa de serviço; o dinheiro cai na conta dele e a taxa na
 * da plataforma. Escrito a partir da API pública de Pagamentos e OAuth; <b>validar com credenciais de teste
 * antes do piloto</b> (PLAN.md M5).
 */
@Component
@ConditionalOnProperty(name = "festa.payments.provider", havingValue = "mercadopago", matchIfMissing = true)
class MercadoPagoGateway implements PaymentGateway {

	private static final String API = "https://api.mercadopago.com";
	private static final String AUTH = "https://auth.mercadopago.com.br/authorization";

	private final RestClient http;
	private final JsonMapper json;
	private final String clientId;
	private final String clientSecret;

	MercadoPagoGateway(JsonMapper json, @Value("${festa.payments.mercadopago.client-id}") String clientId,
			@Value("${festa.payments.mercadopago.client-secret}") String clientSecret) {
		this.http = RestClient.builder().baseUrl(API).build();
		this.json = json;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public Payment.Provider provider() {
		return Payment.Provider.MERCADO_PAGO;
	}

	@Override
	public GatewayPayment createPix(Seller seller, PixCharge charge) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("transaction_amount", reais(charge.amountCents()));
		body.put("application_fee", reais(charge.platformFeeCents()));
		body.put("description", charge.description());
		body.put("payment_method_id", "pix");
		body.put("external_reference", charge.externalReference());
		body.put("notification_url", charge.notificationUrl());
		body.put("date_of_expiration", OffsetDateTime.ofInstant(charge.expiresAt(), java.time.ZoneOffset.UTC).toString());
		body.put("payer", Map.of("email", charge.payer().email(), "first_name", charge.payer().name()));
		return parse(post("/v1/payments", seller, charge.idempotencyKey(), body));
	}

	@Override
	public GatewayPayment createCard(Seller seller, CardCharge charge) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("transaction_amount", reais(charge.amountCents()));
		body.put("application_fee", reais(charge.platformFeeCents()));
		body.put("description", charge.description());
		body.put("token", charge.cardToken());
		body.put("installments", 1);
		body.put("payment_method_id", charge.paymentMethodId());
		if (charge.issuerId() != null) {
			body.put("issuer_id", charge.issuerId());
		}
		body.put("external_reference", charge.externalReference());
		body.put("notification_url", charge.notificationUrl());
		body.put("payer", Map.of("email", charge.payer().email()));
		return parse(post("/v1/payments", seller, charge.idempotencyKey(), body));
	}

	@Override
	public GatewayPayment fetch(Seller seller, String paymentId) {
		String response = http.get()
			.uri("/v1/payments/{id}", paymentId)
			.header("Authorization", "Bearer " + seller.accessToken())
			.retrieve()
			.body(String.class);
		return parse(response);
	}

	@Override
	public void refund(Seller seller, String paymentId, String idempotencyKey) {
		http.post()
			.uri("/v1/payments/{id}/refunds", paymentId)
			.header("Authorization", "Bearer " + seller.accessToken())
			.header("X-Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{}")
			.retrieve()
			.toBodilessEntity();
	}

	@Override
	public String authorizationUrl(String state, String redirectUri) {
		return UriComponentsBuilder.fromUriString(AUTH)
			.queryParam("client_id", clientId)
			.queryParam("response_type", "code")
			.queryParam("platform_id", "mp")
			.queryParam("state", state)
			.queryParam("redirect_uri", redirectUri)
			.encode()
			.toUriString();
	}

	@Override
	public Tokens exchangeCode(String code, String redirectUri) {
		return tokens(Map.of("client_id", clientId, "client_secret", clientSecret, "grant_type", "authorization_code",
				"code", code, "redirect_uri", redirectUri));
	}

	@Override
	public Tokens refresh(String refreshToken) {
		return tokens(Map.of("client_id", clientId, "client_secret", clientSecret, "grant_type", "refresh_token",
				"refresh_token", refreshToken));
	}

	private Tokens tokens(Map<String, String> body) {
		JsonNode node = json.readTree(http.post()
			.uri("/oauth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.body(json.writeValueAsString(body))
			.retrieve()
			.body(String.class));
		long expiresIn = node.path("expires_in").asLong(0);
		return new Tokens(node.path("user_id").asText(), node.path("access_token").asText(),
				node.path("refresh_token").asText(null), node.path("public_key").asText(null),
				expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);
	}

	private String post(String path, Seller seller, String idempotencyKey, Map<String, Object> body) {
		return http.post()
			.uri(path)
			.header("Authorization", "Bearer " + seller.accessToken())
			.header("X-Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.body(json.writeValueAsString(body))
			.retrieve()
			.body(String.class);
	}

	private GatewayPayment parse(String response) {
		JsonNode node = json.readTree(response);
		JsonNode pix = node.path("point_of_interaction").path("transaction_data");
		String expiration = node.path("date_of_expiration").asText(null);
		return new GatewayPayment(node.path("id").asText(), status(node.path("status").asText()),
				node.path("status_detail").asText(null), cents(node.path("transaction_amount").decimalValue()),
				node.path("external_reference").asText(null), pix.path("qr_code").asText(null),
				pix.path("qr_code_base64").asText(null),
				expiration == null ? null : OffsetDateTime.parse(expiration).toInstant(), sanitized(node));
	}

	/** Sem dados de cartão nem documento do pagador no que fica guardado. */
	private String sanitized(JsonNode node) {
		if (node instanceof ObjectNode object) {
			ObjectNode copy = object.deepCopy();
			copy.remove("card");
			if (copy.path("payer") instanceof ObjectNode payer) {
				payer.remove("identification");
			}
			return json.writeValueAsString(copy);
		}
		return null;
	}

	static Payment.Status status(String mercadoPago) {
		return switch (mercadoPago) {
			case "approved" -> Payment.Status.APPROVED;
			case "rejected" -> Payment.Status.REJECTED;
			case "cancelled" -> Payment.Status.CANCELLED;
			case "refunded" -> Payment.Status.REFUNDED;
			case "charged_back" -> Payment.Status.CHARGED_BACK;
			default -> Payment.Status.PENDING; // pending, in_process, authorized, in_mediation
		};
	}

	/** Centavos → reais com 2 casas (a API usa decimal). */
	static BigDecimal reais(long cents) {
		return BigDecimal.valueOf(cents, 2);
	}

	static long cents(BigDecimal reais) {
		return reais.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
	}

}
