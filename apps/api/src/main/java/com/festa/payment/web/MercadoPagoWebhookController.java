package com.festa.payment.web;

import com.festa.payment.app.PaymentService;
import com.festa.payment.app.WebhookSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Webhook do Mercado Pago (ARCHITECTURE.md §Pagamento, passo 4): valida a assinatura, ignora o que não é
 * pagamento e concilia consultando o gateway. Responde 200 rápido para tudo que foi aceito; assinatura
 * inválida é 401 (o gateway reenvia, e o log mostra a tentativa).
 */
@RestController
class MercadoPagoWebhookController {

	private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

	private final PaymentService payments;
	private final JsonMapper json;
	private final String secret;

	MercadoPagoWebhookController(PaymentService payments, JsonMapper json,
			@Value("${festa.payments.mercadopago.webhook-secret}") String secret) {
		this.payments = payments;
		this.json = json;
		this.secret = secret;
	}

	@PostMapping("/api/v1/webhooks/mercadopago")
	ResponseEntity<Void> receive(@RequestHeader(name = "x-signature", required = false) String signature,
			@RequestHeader(name = "x-request-id", required = false) String requestId,
			@RequestParam(name = "data.id", required = false) String dataIdParam,
			@RequestParam(name = "type", required = false) String typeParam, @RequestBody(required = false) String body) {
		JsonNode node = body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
		String dataId = dataIdParam != null ? dataIdParam : node.path("data").path("id").asText(null);
		String type = typeParam != null ? typeParam : node.path("type").asText(null);
		if (!WebhookSignature.valid(secret, signature, requestId, dataId)) {
			log.warn("Webhook do Mercado Pago com assinatura inválida (request-id {})", requestId);
			return ResponseEntity.status(401).build();
		}
		if (!"payment".equals(type)) {
			return ResponseEntity.ok().build();
		}
		payments.reconcile(dataId);
		return ResponseEntity.ok().build();
	}

}
