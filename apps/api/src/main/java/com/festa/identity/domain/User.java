package com.festa.identity.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

	private String name;

	private String email;

	private String passwordHash;

	private Instant emailVerifiedAt;

	protected User() {
	}

	private User(String name, String email, String passwordHash) {
		this.name = name == null ? null : requireText(name, "name").trim();
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
	}

	/** Cadastro por e-mail + senha. A senha chega já como hash Argon2id. */
	public static User registerWithPassword(String name, String email, String passwordHash) {
		return new User(requireText(name, "name"), email, requireText(passwordHash, "passwordHash"));
	}

	/**
	 * Conta criada no primeiro acesso por link mágico (ADR-004): sem nome e sem senha,
	 * com e-mail já verificado porque o clique no link prova a posse da caixa.
	 */
	public static User registerFromVerifiedEmail(String email, Instant verifiedAt) {
		User user = new User(null, email, null);
		user.emailVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
		return user;
	}

	/**
	 * Registra que o dono da caixa de e-mail provou a posse (ex.: clicou no link mágico).
	 * Se a conta ainda não era verificada, a senha existente foi definida por alguém que nunca
	 * provou ser dono do e-mail e é descartada (proteção contra conta pré-criada por terceiro).
	 *
	 * @return true se credenciais anteriores foram invalidadas e as sessões abertas devem ser encerradas
	 */
	public boolean confirmEmailOwnership(Instant verifiedAt) {
		Objects.requireNonNull(verifiedAt, "verifiedAt");
		if (emailVerifiedAt != null) {
			return false;
		}
		emailVerifiedAt = verifiedAt;
		boolean hadPassword = passwordHash != null;
		passwordHash = null;
		return hadPassword;
	}

	/** E-mail é guardado sem espaços e em minúsculas (a migration V001 exige). */
	public static String normalizeEmail(String email) {
		return requireText(email, "email").trim().toLowerCase(Locale.ROOT);
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " não pode ser vazio");
		}
		return value;
	}

}
