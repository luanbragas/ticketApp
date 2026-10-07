package com.festa.organization.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slug de URL: minúsculas, números e hífens; 3 a 60 caracteres (mesma regra do CHECK da migration V001). */
public final class Slug {

	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 60;

	private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

	private Slug() {
	}

	public static boolean isValid(String slug) {
		return slug != null && slug.length() >= MIN_LENGTH && slug.length() <= MAX_LENGTH
			&& VALID.matcher(slug).matches();
	}

	/** "Atlética de Medicina — UFMG" → "atletica-de-medicina-ufmg". Pode devolver menos de 3 caracteres. */
	public static String fromName(String name) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-+)|(-+$)", "");
		if (slug.length() > MAX_LENGTH) {
			slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
		}
		return slug;
	}

	/** Variação para desempate: "atletica" + 2 → "atletica-2", cortando a base se passar do tamanho máximo. */
	public static String withSuffix(String base, int suffix) {
		String tail = "-" + suffix;
		String head = base.length() + tail.length() > MAX_LENGTH
			? base.substring(0, MAX_LENGTH - tail.length()).replaceAll("-+$", "")
			: base;
		return head + tail;
	}

}
