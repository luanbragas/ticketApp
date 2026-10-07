package com.festa.identity.web;

import com.festa.identity.app.MagicLinkService;
import com.festa.identity.domain.User;
import com.festa.identity.web.AuthController.MeResponse;
import com.festa.shared.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Link mágico: pedir (público, sempre 202) e consumir (abre a sessão). */
@RestController
class MagicLinkController {

	private final MagicLinkService magicLinks;
	private final SessionAuthenticator sessionAuthenticator;

	MagicLinkController(MagicLinkService magicLinks, SessionAuthenticator sessionAuthenticator) {
		this.magicLinks = magicLinks;
		this.sessionAuthenticator = sessionAuthenticator;
	}

	record MagicLinkRequest(
			@NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.")
			@Size(max = 320, message = "E-mail muito longo.") String email) {
	}

	record ConsumeRequest(@NotBlank(message = "Link inválido.") @Size(max = 100, message = "Link inválido.") String token) {
	}

	@PostMapping("/api/v1/public/magic-links")
	ResponseEntity<Void> request(@Valid @RequestBody MagicLinkRequest body) {
		magicLinks.request(body.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/api/v1/auth/magic-link/consume")
	MeResponse consume(@Valid @RequestBody ConsumeRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = magicLinks.consume(body.token());
		sessionAuthenticator.startSession(user.getId(), request, response);
		return MeResponse.of(user);
	}

}
