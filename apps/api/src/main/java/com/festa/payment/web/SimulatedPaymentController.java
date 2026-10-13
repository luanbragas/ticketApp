package com.festa.payment.web;

import com.festa.payment.app.FakePaymentGateway;
import com.festa.payment.app.PaymentService;
import com.festa.payment.domain.Payment;
import com.festa.shared.web.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Só existe com o gateway simulado (desenvolvimento local, ADR-011): "o comprador pagou o PIX". Não aprova o
 * pedido direto: marca o pagamento simulado como pago e roda a mesma conciliação do webhook.
 */
@RestController
@ConditionalOnProperty(name = "festa.payments.provider", havingValue = "fake")
class SimulatedPaymentController {

	private final PaymentService payments;
	private final FakePaymentGateway gateway;

	SimulatedPaymentController(PaymentService payments, FakePaymentGateway gateway) {
		this.payments = payments;
		this.gateway = gateway;
	}

	@PostMapping("/api/v1/public/orders/{orderId}/payment/simulate-approval")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void approve(@PathVariable UUID orderId, @RequestHeader(name = "X-Order-Key", required = false) String key) {
		Payment payment = payments.current(orderId, key)
			.map(PaymentService.View::payment)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Não encontrado",
				"Gere o PIX antes de simular o pagamento."));
		gateway.approve(payment.getProviderPaymentId());
		payments.reconcile(payment.getProviderPaymentId());
	}

}
