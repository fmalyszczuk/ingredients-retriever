package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresTestDatabase;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sessions live in PostgreSQL, so a login survives the backend being stopped and started again. Each "run" is a
 * complete, separate start of the application (new Spring context, new web server, new connection pool).
 */
@RequiresDocker
class SessionPersistenceIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";

    private ConfigurableApplicationContext startApplication() {
        PostgreSQLContainer postgres = PostgresTestDatabase.get();
        return new SpringApplicationBuilder(IngredientsRetrieverApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0",
                        "--pantrypal.security.bcrypt-strength=4",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword());
    }

    private HttpBrowser browserFor(ConfigurableApplicationContext context) {
        return new HttpBrowser(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
    }

    @BeforeEach
    void emptyAccountsAndSessions() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("DELETE FROM spring_session");
            jdbc.update("DELETE FROM users");
        }
    }

    private String json(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}";
    }

    private void registerAndLogIn(HttpBrowser browser, String email) throws Exception {
        browser.get("/auth/csrf");
        assertEquals(202, browser.sendWithCsrf("POST", "/auth/register", json(email)).statusCode());
        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", json(email)).statusCode());
    }

    @Test
    void aLogin_survivesRestartingTheBackend_repeatedly() throws Exception {
        HttpBrowser firstBrowser;
        try (ConfigurableApplicationContext first = startApplication()) {
            firstBrowser = browserFor(first);
            registerAndLogIn(firstBrowser, "alice@example.com");
            assertEquals(200, firstBrowser.get("/auth/me").statusCode());
        }

        for (int run = 2; run <= 3; run++) {
            try (ConfigurableApplicationContext again = startApplication()) {
                // The same cookies the browser kept, sent to a brand new process.
                HttpBrowser sameBrowser = browserFor(again);
                sameBrowser.cookies().putAll(firstBrowser.cookies());

                HttpResponse<String> me = sameBrowser.get("/auth/me");

                assertEquals(200, me.statusCode(), "run " + run + ": the user must still be logged in");
                assertEquals(200, sameBrowser.get("/shopping-list").statusCode());
            }
        }
    }

    @Test
    void aLogout_isRememberedAcrossARestart_tooSoALoggedOutCookieStaysDead() throws Exception {
        HttpBrowser browser;
        String cookieBeforeLogout;
        try (ConfigurableApplicationContext first = startApplication()) {
            browser = browserFor(first);
            registerAndLogIn(browser, "bob@example.com");
            cookieBeforeLogout = browser.cookies().get(sessionCookieName(browser));
            assertEquals(204, browser.sendWithCsrf("POST", "/auth/logout", null).statusCode());
        }

        try (ConfigurableApplicationContext again = startApplication()) {
            HttpBrowser thief = browserFor(again);
            thief.cookies().put("PANTRYPAL_SESSION", cookieBeforeLogout);

            assertEquals(401, thief.get("/auth/me").statusCode());
        }
    }

    @Test
    void aSessionThatHasExpired_isNotAccepted_evenThoughItIsStillInTheTable() throws Exception {
        try (ConfigurableApplicationContext context = startApplication()) {
            HttpBrowser browser = browserFor(context);
            registerAndLogIn(browser, "carol@example.com");
            assertEquals(200, browser.get("/auth/me").statusCode());

            // pretend the eight hours of inactivity have passed: Spring Session works expiry out from the last access time
            // (expiry_time is only what its cleanup job looks at), so both are moved to the distant past
            context.getBean(JdbcTemplate.class).update("UPDATE spring_session SET last_access_time = 1, expiry_time = 1");

            assertEquals(401, browser.get("/auth/me").statusCode());
            assertEquals(401, browser.get("/shopping-list").statusCode());
        }
    }

    @Test
    void theSessionRecordsWhoOwnsItAndHowLongItMayBeIdle() throws Exception {
        try (ConfigurableApplicationContext context = startApplication()) {
            HttpBrowser browser = browserFor(context);
            registerAndLogIn(browser, "dave@example.com");

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            assertEquals("dave@example.com", jdbc.queryForObject("SELECT principal_name FROM spring_session", String.class));
            assertEquals(8 * 60 * 60, jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session", Integer.class));
        }
    }

    @Test
    void aVeryLongEmailAddress_canStillLogIn() throws Exception {
        // 254 characters is the longest valid email; Spring Session's stock table would only fit 100 of them.
        String longEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
        assertEquals(254, longEmail.length());
        try (ConfigurableApplicationContext context = startApplication()) {
            HttpBrowser browser = browserFor(context);

            registerAndLogIn(browser, longEmail);

            assertEquals(200, browser.get("/auth/me").statusCode());
            assertEquals(longEmail, context.getBean(JdbcTemplate.class).queryForObject("SELECT principal_name FROM spring_session", String.class));
        }
    }

    private String sessionCookieName(HttpBrowser browser) {
        String name = browser.cookies().keySet().stream().filter(n -> !n.equals("XSRF-TOKEN")).findFirst().orElse(null);
        assertNotNull(name, "a session cookie must have been issued");
        return name;
    }
}
