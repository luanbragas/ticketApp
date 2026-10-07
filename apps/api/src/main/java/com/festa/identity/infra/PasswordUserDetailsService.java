package com.festa.identity.infra;

import com.festa.identity.domain.User;
import com.festa.shared.security.AuthenticatedUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o usuário para login por e-mail + senha. Conta sem senha (só Google ou link mágico)
 * é tratada como inexistente; o DaoAuthenticationProvider ainda roda o hash para não vazar tempo.
 */
@Service
class PasswordUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	PasswordUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		if (email == null || email.isBlank()) {
			throw new UsernameNotFoundException("not found");
		}
		return users.findByEmail(User.normalizeEmail(email))
			.filter(user -> user.getPasswordHash() != null)
			.map(user -> new AuthenticatedUser(user.getId(), user.getPasswordHash()))
			.orElseThrow(() -> new UsernameNotFoundException("not found"));
	}

}
