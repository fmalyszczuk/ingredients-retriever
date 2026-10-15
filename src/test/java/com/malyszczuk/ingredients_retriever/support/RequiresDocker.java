package com.malyszczuk.ingredients_retriever.support;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Skips the test class (instead of failing it) on a machine without a running Docker, which the database tests need. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnabledIf(value = "com.malyszczuk.ingredients_retriever.support.DockerAvailable#isAvailable",
        disabledReason = "Docker is not available, so the PostgreSQL integration tests are skipped")
public @interface RequiresDocker {
}
