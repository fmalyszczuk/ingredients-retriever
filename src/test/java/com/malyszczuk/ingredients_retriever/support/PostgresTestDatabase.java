package com.malyszczuk.ingredients_retriever.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** One real PostgreSQL (the same image as docker-compose.yml) shared by every database test in the run. */
public final class PostgresTestDatabase {

    private static PostgreSQLContainer container;

    private PostgresTestDatabase() {
    }

    /** Starts the database on first use; it is removed automatically when the test JVM exits. */
    public static synchronized PostgreSQLContainer get() {
        if (container == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
        }
        return container;
    }
}
