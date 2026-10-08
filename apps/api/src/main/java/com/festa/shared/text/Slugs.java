package com.festa.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slug de URL: minúsculas, números e hífens. Cada recurso define o próprio tamanho máximo. */
public final class Slugs {

	public static final int MIN_LENGTH = 3;

	private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

	private Slugs() {
	}

	public static boolean isValid(String slug, int maxLength) {
		return slug != null && slug.length() >= MIN_LENGTH && slug.length() <= maxLength
			&& VALID.matcher(slug).matches();
	}

	/** "Atlética de Medicina — UFMG" → "atletica-de-medicina-ufmg". Pode devolver menos de 3 caracteres. */
	public static String fromName(String name, int maxLength) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-+)|(-+$)", "");
		if (slug.length() > maxLength) {
			slug = slug.substring(0, maxLength).replaceAll("-+$", "");
		}
		return slug;
	}

	/** Variação para desempate: "atletica" + 2 → "atletica-2", cortando a base se passar do tamanho máximo. */
	public static String withSuffix(String base, int suffix, int maxLength) {
		String tail = "-" + suffix;
		String head = base.length() + tail.length() > maxLength
			? base.substring(0, maxLength - tail.length()).replaceAll("-+$", "")
			: base;
		return head + tail;
	}

}
