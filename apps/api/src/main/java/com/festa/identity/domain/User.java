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
		this.name = requireText(name, "name").trim();
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
	}

	/** Cadastro por e-mail + senha. A senha chega já como hash Argon2id. */
	public static User registerWithPassword(String name, String email, String passwordHash) {
		return new User(name, email, requireText(passwordHash, "passwordHash"));
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
