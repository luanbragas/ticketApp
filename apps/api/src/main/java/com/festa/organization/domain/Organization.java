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
