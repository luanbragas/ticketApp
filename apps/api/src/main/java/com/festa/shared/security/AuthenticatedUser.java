package com.festa.shared.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Usuário autenticado guardado na sessão. O "username" é o id do usuário, para a tabela de sessões
 * não guardar e-mail. Papéis são por organização e checados pelo TenantGuard, não aqui.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	private static final long serialVersionUID = 1L;

	private final UUID id;
	private String passwordHash;

	public AuthenticatedUser(UUID id, String passwordHash) {
		this.id = id;
		this.passwordHash = passwordHash;
	}

	public UUID id() {
		return id;
	}

	@Override
	public String getUsername() {
		return id.toString();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

}
