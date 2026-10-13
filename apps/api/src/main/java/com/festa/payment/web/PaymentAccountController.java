package com.festa.payment.web;

import com.festa.payment.app.PaymentAccountService;
import com.festa.payment.app.PaymentAccountService.Status;
import com.festa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Conta de recebimento da organização no gateway (OAuth, ADR-003). */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/payment-account")
class PaymentAccountController {

	private final PaymentAccountService accounts;

	PaymentAccountController(PaymentAccountService accounts) {
		this.accounts = accounts;
	}

	record ConnectResponse(String authorizationUrl) {
	}

	record CallbackRequest(@NotBlank @Size(max = 500) String code, @NotBlank @Size(max = 100) String state) {
	}

	@GetMapping
	Status status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		return accounts.status(orgId, user.id());
	}

	@PostMapping("/connect")
	ConnectResponse connect(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		return new ConnectResponse(accounts.connect(orgId, user.id()));
	}

	@PostMapping("/callback")
	Status callback(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId,
			@Valid @RequestBody CallbackRequest body) {
		return accounts.callback(orgId, user.id(), body.code(), body.state());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orgId) {
		accounts.disconnect(orgId, user.id());
	}

}
