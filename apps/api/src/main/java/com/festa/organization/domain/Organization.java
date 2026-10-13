package com.festa.organization.domain;

import com.festa.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

	private String name;

	private String slug;

	private String logoUrl;

	private String instagram;

	private String whatsapp;

	protected Organization() {
	}

	public Organization(String name, String slug) {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name não pode ser vazio");
		}
		if (!Slug.isValid(slug)) {
			throw new IllegalArgumentException("slug inválido: " + slug);
		}
		this.name = name.trim();
		this.slug = slug;
	}

	/**
	 * Dados públicos da organização (aparecem na página do evento). {@code null} mantém; texto vazio apaga
	 * Instagram e WhatsApp. Instagram vira só o usuário (sem @ nem link); WhatsApp, só dígitos.
	 */
	public void updateProfile(String newName, String newInstagram, String newWhatsapp) {
		if (newName != null) {
			if (newName.isBlank() || newName.trim().length() > 80) {
				throw new IllegalArgumentException("nome inválido");
			}
			name = newName.trim();
		}
		if (newInstagram != null) {
			String handle = newInstagram.trim()
				.replaceFirst("(?i)^(https?://)?(www\\.)?instagram\\.com/", "")
				.replaceFirst("^@", "")
				.replaceFirst("/+$", "");
			if (!handle.isEmpty() && !handle.matches("^[A-Za-z0-9._]{1,30}$")) {
				throw new IllegalArgumentException("instagram inválido");
			}
			instagram = handle.isEmpty() ? null : handle;
		}
		if (newWhatsapp != null) {
			String digits = newWhatsapp.replaceAll("\\D", "");
			if (!digits.isEmpty() && (digits.length() < 10 || digits.length() > 15)) {
				throw new IllegalArgumentException("whatsapp inválido");
			}
			whatsapp = digits.isEmpty() ? null : digits;
		}
	}

	/** Logo já enviada ao bucket; {@code null} tira a logo. */
	public void changeLogo(String url) {
		logoUrl = url;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	public String getInstagram() {
		return instagram;
	}

	public String getWhatsapp() {
		return whatsapp;
	}

}
