package com.festa;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Fronteiras de módulo descritas em docs/BACKEND.md. */
@AnalyzeClasses(packages = "com.festa", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

	private static final String ROOT = "com.festa.";

	@ArchTest
	static final ArchRule modules_only_import_other_modules_api_or_shared = classes()
		.that().resideInAPackage("com.festa..")
		.should(onlyDependOnOtherModulesThroughApi())
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule domain_does_not_depend_on_web_or_infra = noClasses()
		.that().resideInAPackage("com.festa.*.domain..")
		.should().dependOnClassesThat().resideInAnyPackage("com.festa.*.web..", "com.festa.*.infra..")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule controllers_do_not_access_repositories = noClasses()
		.that().resideInAPackage("com.festa.*.web..")
		.should().dependOnClassesThat().areAssignableTo(Repository.class)
		.allowEmptyShould(true);

	static ArchCondition<JavaClass> onlyDependOnOtherModulesThroughApi() {
		return new ArchCondition<>("only depend on other modules through their api package") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originModule = moduleOf(origin);
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					JavaClass target = dependency.getTargetClass();
					String targetModule = moduleOf(target);
					boolean allowed = targetModule == null
						|| targetModule.equals(originModule)
						|| targetModule.equals("shared")
						|| target.getPackageName().startsWith(ROOT + targetModule + ".api");
					if (!allowed) {
						events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
					}
				}
			}
		};
	}

	/** Nome do módulo (primeiro pacote abaixo de com.festa) ou null fora de um módulo. */
	private static String moduleOf(JavaClass javaClass) {
		String pkg = javaClass.getPackageName();
		if (!pkg.startsWith(ROOT)) {
			return null;
		}
		int end = pkg.indexOf('.', ROOT.length());
		return end < 0 ? pkg.substring(ROOT.length()) : pkg.substring(ROOT.length(), end);
	}

}
