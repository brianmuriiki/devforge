package com.devforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the modular-monolith boundaries. Every module is isolated from the others. */
@AnalyzeClasses(packages = "com.devforge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] MODULES = {
        "com.devforge.tasks..", "com.devforge.search..", "com.devforge.security..", "com.devforge.environment.."
    };

    @ArchTest
    static final ArchRule tasksIsIsolated = noClasses()
            .that().resideInAPackage("com.devforge.tasks..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devforge.search..", "com.devforge.security..", "com.devforge.environment..",
                    "com.devforge.integration..");

    @ArchTest
    static final ArchRule searchIsIsolated = noClasses()
            .that().resideInAPackage("com.devforge.search..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devforge.tasks..", "com.devforge.security..", "com.devforge.environment..",
                    "com.devforge.integration..");

    @ArchTest
    static final ArchRule securityIsIsolated = noClasses()
            .that().resideInAPackage("com.devforge.security..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devforge.tasks..", "com.devforge.search..", "com.devforge.environment..",
                    "com.devforge.integration..");

    @ArchTest
    static final ArchRule environmentIsIsolated = noClasses()
            .that().resideInAPackage("com.devforge.environment..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devforge.tasks..", "com.devforge.search..", "com.devforge.security..",
                    "com.devforge.integration..");

    @ArchTest
    static final ArchRule coreDoesNotKnowAnyModule = noClasses()
            .that().resideInAPackage("com.devforge.core..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULES);

    @ArchTest
    static final ArchRule coreDoesNotKnowTheCliOrIntegration = noClasses()
            .that().resideInAPackage("com.devforge.core..")
            .should().dependOnClassesThat().resideInAnyPackage("com.devforge.cli..", "com.devforge.integration..");

    @ArchTest
    static final ArchRule modelsAreFreeOfSql = noClasses()
            .that().resideInAnyPackage(
                    "com.devforge.tasks.model..", "com.devforge.search.model..", "com.devforge.security.model..",
                    "com.devforge.environment.model..")
            .should().dependOnClassesThat().resideInAPackage("java.sql..");

    @ArchTest
    static final ArchRule businessLogicDoesNotKnowTheCli = noClasses()
            .that().resideInAnyPackage(
                    "com.devforge.tasks.model..", "com.devforge.tasks.repo..", "com.devforge.tasks.service..",
                    "com.devforge.search.model..", "com.devforge.search.scan..", "com.devforge.search.repo..",
                    "com.devforge.search.service..", "com.devforge.security.model..", "com.devforge.security.parse..",
                    "com.devforge.security.provider..", "com.devforge.security.repo..", "com.devforge.security.service..",
                    "com.devforge.environment.model..", "com.devforge.environment.engine..",
                    "com.devforge.environment.repo..", "com.devforge.environment.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "picocli..", "com.devforge.cli..", "com.devforge.tasks.cli..", "com.devforge.search.cli..",
                    "com.devforge.security.cli..", "com.devforge.environment.cli..");

    @ArchTest
    static final ArchRule noWebStackAnywhere = noClasses()
            .that().resideInAPackage("com.devforge..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "javax.servlet..", "jakarta.servlet..", "com.sun.net.httpserver..");
}
