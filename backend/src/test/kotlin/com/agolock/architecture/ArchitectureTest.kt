package com.agolock.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(
    packages = ["com.agolock"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {
    @ArchTest
    val persistenceMustNotDependOnApiOrService: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..service..")

    @ArchTest
    val apiMustNotDependOnPersistence: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..persistence..")

    @ArchTest
    val domainMustNotDependOnOuterLayers: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..persistence..", "..service..")
}
