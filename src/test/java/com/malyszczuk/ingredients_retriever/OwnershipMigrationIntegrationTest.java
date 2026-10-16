package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.PostgresTestDatabase;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What V4 does to a database that already holds data from before every row had an owner. */
@RequiresDocker
class OwnershipMigrationIntegrationTest {

    private JdbcTemplate freshDatabaseAt(String name, String targetVersion) {
        PostgreSQLContainer postgres = PostgresTestDatabase.get();
        DriverManagerDataSource admin = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(admin).execute("DROP DATABASE IF EXISTS " + name);
        new JdbcTemplate(admin).execute("CREATE DATABASE " + name);

        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).target(targetVersion).load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private void migrateToLatest(JdbcTemplate jdbc) {
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
    }

    @Test
    void existingData_goesToTheOldestAccount() {
        JdbcTemplate jdbc = freshDatabaseAt("migration_with_users", "3");
        jdbc.update("INSERT INTO users (email, password_hash, created_at) VALUES ('first@example.com', 'x', now()), ('second@example.com', 'x', now())");
        jdbc.update("INSERT INTO shopping_list_items (name) VALUES ('eggs')");
        jdbc.update("INSERT INTO recipes (title, source_type, created_at) VALUES ('Pancakes', 'MANUAL', now())");

        migrateToLatest(jdbc);

        Long oldest = jdbc.queryForObject("SELECT min(id) FROM users", Long.class);
        assertEquals(oldest, jdbc.queryForObject("SELECT user_id FROM shopping_list_items WHERE name = 'eggs'", Long.class));
        assertEquals(oldest, jdbc.queryForObject("SELECT user_id FROM recipes WHERE title = 'Pancakes'", Long.class));
    }

    @Test
    void dataNobodyCouldOwn_isDropped_insteadOfFailingTheMigration() {
        JdbcTemplate jdbc = freshDatabaseAt("migration_without_users", "3");
        jdbc.update("INSERT INTO shopping_list_items (name) VALUES ('eggs')");
        jdbc.update("INSERT INTO recipes (title, source_type, created_at) VALUES ('Pancakes', 'MANUAL', now())");

        migrateToLatest(jdbc);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM recipes", Integer.class));
    }

    @Test
    void accountsThatExistedBeforeEmailVerification_countAsVerified_soNobodyIsLockedOut() {
        JdbcTemplate jdbc = freshDatabaseAt("migration_verification", "4");
        jdbc.update("INSERT INTO users (email, password_hash, created_at) VALUES ('old@example.com', 'x', now())");

        migrateToLatest(jdbc);

        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT email_verified FROM users WHERE email = 'old@example.com'", Boolean.class));
        jdbc.update("INSERT INTO users (email, password_hash, created_at) VALUES ('new@example.com', 'x', now())");
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT email_verified FROM users WHERE email = 'new@example.com'", Boolean.class),
                "accounts created from now on start unverified");
    }
}
