package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The same cookies when the application is told it is served over HTTPS (PANTRYPAL_COOKIE_SECURE=true). */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"pantrypal.security.bcrypt-strength=4", "pantrypal.security.cookie-secure=true"})
class SecureCookiesIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bothCookies_areMarkedSecure_soABrowserNeverSendsThemOverPlainHttp() throws Exception {
        jdbc.update("DELETE FROM users");
        HttpBrowser browser = new HttpBrowser(port);

        browser.get("/auth/csrf");
        String csrfCookie = browser.lastSetCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertTrue(csrfCookie.contains("Secure"), csrfCookie);

        // The browser would refuse to send Secure cookies back over http, so the test sends them by hand.
        String body = "{\"email\": \"alice@example.com\", \"password\": \"a very decent passphrase\"}";
        assertEquals(202, browser.sendWithCsrf("POST", "/auth/register", body).statusCode());
        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", body).statusCode());

        String sessionCookie = browser.lastSetCookie("PANTRYPAL_SESSION");
        assertNotNull(sessionCookie);
        assertTrue(sessionCookie.contains("Secure"), sessionCookie);
        assertTrue(sessionCookie.contains("HttpOnly"), sessionCookie);
        assertTrue(sessionCookie.contains("SameSite=Lax"), sessionCookie);
    }
}
