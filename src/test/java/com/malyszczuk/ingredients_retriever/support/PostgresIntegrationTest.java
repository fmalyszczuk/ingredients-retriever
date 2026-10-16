package com.malyszczuk.ingredients_retriever.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base for tests that run the whole application against a real PostgreSQL. Subclasses add {@link RequiresDocker}. */
// A low BCrypt work factor keeps the tests fast; production uses 12 (see PasswordConfig).
@SpringBootTest(properties = "pantrypal.security.bcrypt-strength=4")
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = PostgresTestDatabase.get();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
