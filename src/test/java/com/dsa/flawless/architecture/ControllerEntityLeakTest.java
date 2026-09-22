package com.dsa.flawless.architecture;


import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class ControllerEntityLeakTest {
    @Test
    void controllers_should_not_expose_jpa_entities() {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .and().arePublic()
                .should((notExposeEntityTypes()));
        rule.check(new ClassFileImporter().importPackages("com.dsa.flawless"));
    }

    private static ArchCondition<JavaMethod> notExposeEntityTypes() {
        return new ArchCondition<>("반환 타입/파라미터에 @Entity 클래스를 포함하지 않는다.") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Set<JavaClass> leaked = new HashSet<>();
                collect(method.getReturnType(), leaked, new HashSet<>());
                method.getParameters().forEach(p->collect(p.getType(), leaked, new HashSet<>()));
                leaked.forEach(entityClass -> events.add(SimpleConditionEvent.violated(
                        method, String.format("%s 가 Entity 타입 %s 를 API 시그니처에 노출합니다", method.getFullName(), entityClass.getName())
                )));
            }
        };
    }

    // 중첩 제네릭까지 재귀로 풀어서 검사하는 코드
    private static void collect(JavaType type, Set<JavaClass> found, Set<JavaType> visited) {
        if(!visited.add(type)) return;

        if(type instanceof JavaParameterizedType parameterized) {
            parameterized.getActualTypeArguments().forEach(arg -> collect(arg, found, visited));
        }

        JavaClass raw = type.toErasure();
        if (raw.isAnnotatedWith(Entity.class)) {
            found.add(raw);
        }
    }
}
