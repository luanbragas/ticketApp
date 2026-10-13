package com.festa.payment.web;

import com.festa.payment.app.RefundService;
import com.festa.payment.app.RefundService.Refunded;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reembolso manual de pedido pago (dono e admin). */
@RestController
class RefundController {

	private final RefundService refunds;

	RefundController(RefundService refunds) {
		this.refunds = refunds;
	}

	record RefundRequest(
			@NotBlank(message = "Informe o motivo.") @Size(max = 500, message = "Motivo muito longo.") String reason) {
	}

	@PostMapping("/api/v1/orgs/{orgId}/orders/{orderId}/refund")
	Refunded refund(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@PathVariable UUID orderId, @Valid @RequestBody RefundRequest body) {
		return refunds.refund(orgId, orderId, user.id(), body.reason().strip());
	}

}
