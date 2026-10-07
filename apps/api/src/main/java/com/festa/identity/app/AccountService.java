package com.festa.identity.app;

import com.festa.identity.domain.User;
import com.festa.identity.infra.UserRepository;
import com.festa.shared.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User signUp(String name, String email, String rawPassword) {
		String normalizedEmail = User.normalizeEmail(email);
		if (users.existsByEmail(normalizedEmail)) {
			throw emailAlreadyRegistered();
		}
		User user = User.registerWithPassword(name, normalizedEmail, passwordEncoder.encode(rawPassword));
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Cadastro concorrente com o mesmo e-mail: o UNIQUE do banco decide.
			throw emailAlreadyRegistered();
		}
	}

	@Transactional(readOnly = true)
	public User get(UUID userId) {
		return users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Não autenticado",
				"Faça login para continuar."));
	}

	private static ApiException emailAlreadyRegistered() {
		return new ApiException(HttpStatus.CONFLICT, "email-already-registered", "E-mail já cadastrado",
			"Já existe uma conta com este e-mail. Entre com sua senha ou recupere o acesso.");
	}

}
