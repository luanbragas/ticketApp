package com.festa.payment.web;

import com.festa.payment.app.PaymentService;
import com.festa.payment.app.PaymentService.View;
import com.festa.payment.domain.Payment;
import com.festa.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Pagamento do pedido pelo comprador (com a chave do pedido, ADR-007). O navegador escolhe só o meio; valor e
 * taxa vêm do pedido. Status final chega pelo webhook: a tela acompanha.
 */
@RestController
@RequestMapping("/api/v1/public/orders/{orderId}/payment")
class PublicPaymentController {

	private final PaymentService payments;

	PublicPaymentController(PaymentService payments) {
		this.payments = payments;
	}

	/** Cartão: token gerado pelo SDK do gateway no navegador (o número nunca chega aqui). */
	record PayRequest(@NotNull Payment.Method method, @Size(max = 200) String cardToken,
			@Size(max = 40) String paymentMethodId, @Size(max = 40) String issuerId) {
	}

	record PaymentResponse(UUID id, Payment.Method method, Payment.Status status, String statusDetail,
			long amountCents, String pixQrCode, String pixQrBase64, Instant pixExpiresAt, boolean simulated) {

		static PaymentResponse of(View view) {
			Payment p = view.payment();
			return new PaymentResponse(p.getId(), p.getMethod(), p.getStatus(), p.getStatusDetail(), p.getAmountCents(),
					p.getPixQrCode(), p.getPixQrBase64(), p.getPixExpiresAt(), view.simulated());
		}

	}

	@PostMapping
	ResponseEntity<PaymentResponse> pay(@PathVariable UUID orderId,
			@RequestHeader(name = "X-Order-Key", required = false) String key, @Valid @RequestBody PayRequest body) {
		View view = switch (body.method()) {
			case PIX -> payments.payWithPix(orderId, key);
			case CARD -> {
				if (body.cardToken() == null || body.paymentMethodId() == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "card-token-required", "Cartão incompleto",
						"Preencha os dados do cartão de novo.");
				}
				yield payments.payWithCard(orderId, key,
						new PaymentService.CardData(body.cardToken(), body.paymentMethodId(), body.issuerId()));
			}
		};
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
			.body(PaymentResponse.of(view));
	}

	@GetMapping
	ResponseEntity<PaymentResponse> current(@PathVariable UUID orderId,
			@RequestHeader(name = "X-Order-Key", required = false) String key) {
		return payments.current(orderId, key)
			.map(view -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PaymentResponse.of(view)))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

}
