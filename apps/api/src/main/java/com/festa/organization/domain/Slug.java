package com.festa.organization.domain;

import com.festa.shared.text.Slugs;

/** Slug de organização: 3 a 60 caracteres (mesma regra do CHECK da migration V001). */
public final class Slug {

	public static final int MIN_LENGTH = Slugs.MIN_LENGTH;
	public static final int MAX_LENGTH = 60;

	private Slug() {
	}

	public static boolean isValid(String slug) {
		return Slugs.isValid(slug, MAX_LENGTH);
	}

	/** "Atlética de Medicina — UFMG" → "atletica-de-medicina-ufmg". Pode devolver menos de 3 caracteres. */
	public static String fromName(String name) {
		return Slugs.fromName(name, MAX_LENGTH);
	}

	/** Variação para desempate: "atletica" + 2 → "atletica-2", cortando a base se passar do tamanho máximo. */
	public static String withSuffix(String base, int suffix) {
		return Slugs.withSuffix(base, suffix, MAX_LENGTH);
	}

}
