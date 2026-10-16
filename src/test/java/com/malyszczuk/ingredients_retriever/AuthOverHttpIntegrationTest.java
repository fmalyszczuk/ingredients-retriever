package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Login against a real running server over HTTP, so the actual cookies, session ids and headers can be checked. */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "pantrypal.security.bcrypt-strength=4")
class AuthOverHttpIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ServerProperties serverProperties;

    private HttpBrowser browser;

    @BeforeEach
    void freshBrowser() {
        jdbc.update("DELETE FROM shopping_list_items");
        jdbc.update("DELETE FROM users");
        browser = new HttpBrowser(port);
    }

    private String json(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private void registerAndLogIn(String email) throws Exception {
        browser.get("/auth/csrf");
        assertEquals(202, browser.sendWithCsrf("POST", "/auth/register", json(email, PASSWORD)).statusCode());
        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", json(email, PASSWORD)).statusCode());
    }

    @Test
    void anonymousRequests_neverGetASessionCookie_soTheyCannotFloodTheServerWithSessions() throws Exception {
        browser.get("/auth/csrf");
        assertNull(browser.lastSetCookie("PANTRYPAL_SESSION"));

        assertEquals(401, browser.get("/shopping-list").statusCode());
        assertNull(browser.lastSetCookie("PANTRYPAL_SESSION"));

        assertEquals(401, browser.get("/does-not-exist").statusCode());
        assertNull(browser.lastSetCookie("PANTRYPAL_SESSION"));
    }

    @Test
    void csrfCookie_canBeReadByTheFrontend_butNotSentFromOtherSites() throws Exception {
        HttpResponse<String> response = browser.get("/auth/csrf");

        String cookie = browser.lastSetCookie("XSRF-TOKEN");
        assertEquals(204, response.statusCode());
        assertNotNull(cookie, "GET /auth/csrf must set the XSRF-TOKEN cookie");
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/"), cookie);
        assertFalse(cookie.contains("HttpOnly"), "the frontend has to read this one: " + cookie);
        assertFalse(cookie.contains("Secure"), "plain http://localhost in the default setup: " + cookie);
    }

    @Test
    void sessionCookie_isHttpOnly_andSameSite_soScriptsAndOtherSitesCannotUseIt() throws Exception {
        registerAndLogIn("alice@example.com");

        String cookie = browser.lastSetCookie("PANTRYPAL_SESSION");

        assertNotNull(cookie, "login must set the PANTRYPAL_SESSION cookie: " + browser.lastSetCookies());
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/"), cookie);
        assertFalse(cookie.contains("Secure"), cookie);
    }

    @Test
    void theSessionCookie_doesNotGiveAwayWhatTheServerIs() throws Exception {
        registerAndLogIn("bob@example.com");

        assertEquals(0, browser.lastSetCookies().stream().filter(c -> c.startsWith("JSESSIONID")).count());
        assertEquals(0, browser.cookies().keySet().stream().filter(name -> name.equals("JSESSIONID")).count());
    }

    @Test
    void aSessionIdPlantedByAnAttacker_isReplacedOnLogin_andNeverWorks() throws Exception {
        browser.get("/auth/csrf");
        browser.cookies().put("PANTRYPAL_SESSION", "planted-by-the-attacker");
        assertEquals(202, browser.sendWithCsrf("POST", "/auth/register", json("carol@example.com", PASSWORD)).statusCode());

        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", json("carol@example.com", PASSWORD)).statusCode());

        String issued = browser.cookies().get("PANTRYPAL_SESSION");
        assertNotNull(issued);
        assertNotEquals("planted-by-the-attacker", issued, "a login must hand out a server-generated session id");
        assertEquals(200, browser.get("/shopping-list").statusCode());

        HttpBrowser attacker = new HttpBrowser(port);
        attacker.cookies().put("PANTRYPAL_SESSION", "planted-by-the-attacker");
        assertEquals(401, attacker.get("/shopping-list").statusCode());
    }

    @Test
    void everyLoginGetsANewSessionId() throws Exception {
        registerAndLogIn("dave@example.com");
        String first = browser.cookies().get("PANTRYPAL_SESSION");

        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", json("dave@example.com", PASSWORD)).statusCode());

        assertNotEquals(first, browser.cookies().get("PANTRYPAL_SESSION"));
    }

    @Test
    void logout_destroysTheSessionOnTheServer_andTellsTheBrowserToDropTheCookie() throws Exception {
        registerAndLogIn("erin@example.com");
        String sessionId = browser.cookies().get("PANTRYPAL_SESSION");
        assertEquals(200, browser.get("/auth/me").statusCode());

        HttpResponse<String> response = browser.sendWithCsrf("POST", "/auth/logout", null);

        assertEquals(204, response.statusCode());
        String dropped = browser.lastSetCookie("PANTRYPAL_SESSION");
        assertNotNull(dropped, "logout must expire the session cookie: " + browser.lastSetCookies());
        // Tomcat expires a cookie with an Expires date in 1970 (some servers use Max-Age=0); either means "delete it"
        assertTrue(dropped.toLowerCase().contains("max-age=0") || dropped.contains("Expires=Thu, 01 Jan 1970"), dropped);

        // even a client that kept the old cookie gets nothing: the session is gone on the server
        HttpBrowser thief = new HttpBrowser(port);
        thief.cookies().put("PANTRYPAL_SESSION", sessionId);
        assertEquals(401, thief.get("/auth/me").statusCode());
        assertEquals(401, thief.get("/shopping-list").statusCode());
    }

    @Test
    void changes_needTheCsrfHeader_overRealHttpToo() throws Exception {
        registerAndLogIn("frank@example.com");
        String body = "{\"name\": \"eggs\", \"quantity\": 2, \"unit\": \"pcs\"}";

        assertEquals(403, browser.send("POST", "/shopping-list/items", body, Map.of()).statusCode());
        assertEquals(403, browser.send("POST", "/shopping-list/items", body, Map.of("X-XSRF-TOKEN", "forged")).statusCode());
        assertEquals(201, browser.sendWithCsrf("POST", "/shopping-list/items", body).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
    }

    @Test
    void theSessionExpiresAfterEightHoursOfInactivity() {
        assertEquals(Duration.ofHours(8), serverProperties.getServlet().getSession().getTimeout());
    }

    @Test
    void theSecurityHeaders_arePresentOnRealResponses() throws Exception {
        registerAndLogIn("grace@example.com");

        HttpResponse<String> response = browser.get("/auth/me");

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(null));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
}
