package com.festa;

import com.festa.fakebeta.app.UsesAlphaApi;
import com.festa.fakebeta.app.UsesAlphaInternal;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/** Garante que a regra de fronteira de módulo realmente detecta violações. */
class ModuleBoundariesSelfTest {

	private final JavaClasses fixtures = new ClassFileImporter()
		.importPackages("com.festa.fakealpha", "com.festa.fakebeta");

	@Test
	void allowsDependencyOnOtherModuleApi() {
		assertThat(evaluate(UsesAlphaApi.class).hasViolation()).isFalse();
	}

	@Test
	void rejectsDependencyOnOtherModuleInternals() {
		EvaluationResult result = evaluate(UsesAlphaInternal.class);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails()).anyMatch(d -> d.contains("AlphaInternal"));
	}

	private EvaluationResult evaluate(Class<?> origin) {
		return classes().that().haveFullyQualifiedName(origin.getName())
			.should(ModuleBoundariesTest.onlyDependOnOtherModulesThroughApi())
			.evaluate(fixtures);
	}

}
