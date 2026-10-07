package com.dsa.flawless.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

//@AnalyzeClasses(packages = "com.dsa.flawless")
//class LayerDependencyTest {
//    @ArchTest
//    static final ArchRule controllers_should_not_depend_on_entities =
//            noClasses().that().resideInAPackage("..controller..")
//                    .should().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity");
//}
