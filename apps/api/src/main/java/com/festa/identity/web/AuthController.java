package com.festa.identity.web;

import com.festa.identity.app.AccountService;
import com.festa.identity.domain.User;
import com.festa.shared.security.AuthenticatedUser;
import com.festa.shared.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Cadastro, login e sessão atual. Logout é tratado pelo Spring Security em POST /api/v1/auth/logout. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AccountService accounts;
	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticator sessionAuthenticator;

	AuthController(AccountService accounts, AuthenticationManager authenticationManager,
			SessionAuthenticator sessionAuthenticator) {
		this.accounts = accounts;
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticator = sessionAuthenticator;
	}

	record SignupRequest(
			@NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "Nome muito longo.") String name,
			@NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.")
			@Size(max = 320, message = "E-mail muito longo.") String email,
			@NotBlank(message = "Informe uma senha.")
			@Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres.") String password) {
	}

	record LoginRequest(
			@NotBlank(message = "Informe seu e-mail.") @Size(max = 320, message = "E-mail muito longo.") String email,
			@NotBlank(message = "Informe sua senha.") @Size(max = 128, message = "Senha muito longa.") String password) {
	}

	record MeResponse(UUID id, String name, String email, boolean emailVerified) {

		static MeResponse of(User user) {
			return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isEmailVerified());
		}

	}

	/** Garante o cookie XSRF-TOKEN antes do primeiro POST da web. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/signup")
	ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.signUp(body.name(), body.email(), body.password());
		sessionAuthenticator.startSession(user.getId(), request, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.of(user));
	}

	@PostMapping("/login")
	MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = authenticationManager.authenticate(
			UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
		sessionAuthenticator.startSession(authentication, request, response);
		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		return MeResponse.of(accounts.get(principal.id()));
	}

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		return MeResponse.of(accounts.get(principal.id()));
	}

}
