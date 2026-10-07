package com.festa.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Cria a sessão de um usuário já autenticado (login ou cadastro), conforme ADR-001:
 * troca o id da sessão (session fixation), grava o SecurityContext e gira o token CSRF.
 */
@Component
public class SessionAuthenticator {

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
	private final SecurityContextRepository securityContextRepository;
	private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;

	SessionAuthenticator(SecurityContextRepository securityContextRepository,
			CsrfAuthenticationStrategy csrfAuthenticationStrategy) {
		this.securityContextRepository = securityContextRepository;
		this.csrfAuthenticationStrategy = csrfAuthenticationStrategy;
	}

	public void startSession(UUID userId, HttpServletRequest request, HttpServletResponse response) {
		startSession(UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId, null), null,
			List.of()), request, response);
	}

	public void startSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = holder.createEmptyContext();
		context.setAuthentication(authentication);
		holder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		csrfAuthenticationStrategy.onAuthentication(authentication, request, response);
		// O token novo é gerado sob demanda; forçamos agora para o cookie XSRF-TOKEN já voltar nesta resposta.
		if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
			csrfToken.getToken();
		}
	}

}
