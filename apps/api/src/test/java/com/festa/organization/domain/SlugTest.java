package com.festa.organization.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugTest {

	@Test
	void buildsSlugFromNameRemovingAccentsAndSymbols() {
		assertThat(Slug.fromName("Atlética de Medicina — UFMG!")).isEqualTo("atletica-de-medicina-ufmg");
		assertThat(Slug.fromName("  Calourada   2026  ")).isEqualTo("calourada-2026");
		assertThat(Slug.fromName("Ação & Coração")).isEqualTo("acao-coracao");
	}

	@Test
	void cutsLongNamesAtMaxLengthWithoutTrailingHyphen() {
		String slug = Slug.fromName("a".repeat(59) + " bbb");

		assertThat(slug).hasSizeLessThanOrEqualTo(Slug.MAX_LENGTH).doesNotEndWith("-");
		assertThat(Slug.isValid(slug)).isTrue();
	}

	@Test
	void addsSuffixKeepingMaxLength() {
		assertThat(Slug.withSuffix("atletica", 2)).isEqualTo("atletica-2");
		assertThat(Slug.withSuffix("a".repeat(60), 12)).hasSize(60).endsWith("-12");
	}

	@Test
	void validatesFormat() {
		assertThat(Slug.isValid("atletica-medicina")).isTrue();
		assertThat(Slug.isValid("ab")).isFalse();
		assertThat(Slug.isValid("Atletica")).isFalse();
		assertThat(Slug.isValid("atletica--medicina")).isFalse();
		assertThat(Slug.isValid("-atletica")).isFalse();
		assertThat(Slug.isValid(null)).isFalse();
	}

}
