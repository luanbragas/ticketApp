package com.festa.promoter.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Promoter da organização: quem divulga o link e vende. Pode ser só um nome com telefone ou um membro
 * com papel PROMOTER ({@code userId}), que então vê as próprias vendas no painel.
 */
@Entity
@Table(name = "promoters")
public class Promoter extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 80;

	private UUID organizationId;

	private UUID userId;

	private String name;

	private String phone;

	protected Promoter() {
	}

	public Promoter(UUID organizationId, UUID userId, String name, String phone) {
		this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
		this.userId = userId;
		update(name, phone);
	}

	/** {@code null} no nome mantém; telefone vazio apaga. */
	public void update(String newName, String newPhone) {
		if (newName != null) {
			String trimmed = newName.trim();
			if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
				throw new IllegalArgumentException("nome inválido");
			}
			name = trimmed;
		}
		if (newPhone != null) {
			String digits = newPhone.replaceAll("[^0-9+]", "");
			phone = digits.isEmpty() ? null : digits;
		}
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

}
