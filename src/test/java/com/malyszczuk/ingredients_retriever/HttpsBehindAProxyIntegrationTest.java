package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application as deployed: PANTRYPAL_REQUIRE_HTTPS=true and PANTRYPAL_FORWARD_HEADERS=native, behind a reverse proxy
 * that terminates TLS. The test is the proxy: it connects over plain HTTP from the local machine (a trusted proxy
 * address) and says what the proxy would say with X-Forwarded-Proto and X-Forwarded-For.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pantrypal.security.bcrypt-strength=4",
        "pantrypal.security.require-https=true",
        "server.forward-headers-strategy=native",
        "pantrypal.security.throttle.login-failures-per-address-and-account=3"})
class HttpsBehindAProxyIntegrationTest extends PostgresIntegrationTest {

    private static final Map<String, String> HTTPS = Map.of("X-Forwarded-Proto", "https");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    private HttpResponse<String> get(HttpBrowser browser, String path, Map<String, String> headers) throws Exception {
        return browser.send("GET", path, null, headers);
    }

    @Test
    void aRequestThatDidNotArriveOverHttps_isRefused_withoutAnyCookie() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);

        HttpResponse<String> plain = get(browser, "/auth/csrf", Map.of());
        HttpResponse<String> claimsHttp = get(browser, "/auth/csrf", Map.of("X-Forwarded-Proto", "http"));

        for (HttpResponse<String> response : java.util.List.of(plain, claimsHttp)) {
            assertEquals(403, response.statusCode());
            assertEquals("HTTPS required", response.body());
            assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        }
    }

    @Test
    void aRequestThatTheProxySaysWasHttps_isServed_withSecureCookiesAndHsts() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);

        HttpResponse<String> response = get(browser, "/auth/csrf", HTTPS);

        assertEquals(204, response.statusCode());
        String csrfCookie = browser.lastSetCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertTrue(csrfCookie.contains("Secure"), "turning HTTPS on also turns Secure cookies on: " + csrfCookie);
        assertEquals("max-age=31536000", response.headers().firstValue("Strict-Transport-Security").orElse(null));
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(null));
    }

    @Test
    void theSessionCookie_isSecureToo() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        get(browser, "/auth/csrf", HTTPS);
        String credentials = "{\"email\": \"alice@example.com\", \"password\": \"a very decent passphrase\"}";
        String token = browser.cookies().get("XSRF-TOKEN");
        Map<String, String> headers = Map.of("X-Forwarded-Proto", "https", "X-XSRF-TOKEN", token);
        jdbc.update("DELETE FROM users");
        assertEquals(201, browser.send("POST", "/auth/register", credentials, headers).statusCode());
        jdbc.update("UPDATE users SET email_verified = true");

        HttpResponse<String> login = browser.send("POST", "/auth/login", credentials, Map.of("X-Forwarded-Proto", "https",
                "X-XSRF-TOKEN", browser.cookies().get("XSRF-TOKEN")));

        assertEquals(200, login.statusCode());
        String session = browser.lastSetCookie("PANTRYPAL_SESSION");
        assertNotNull(session);
        assertTrue(session.contains("Secure") && session.contains("HttpOnly"), session);
    }

    @Test
    void theRateLimitsSeeTheRealClient_notTheProxy() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        get(browser, "/auth/csrf", HTTPS);
        String body = "{\"email\": \"victim@example.com\", \"password\": \"wrong wrong wrong\"}";
        Map<String, String> attacker = Map.of("X-Forwarded-Proto", "https", "X-Forwarded-For", "203.0.113.50",
                "X-XSRF-TOKEN", browser.cookies().get("XSRF-TOKEN"));
        Map<String, String> bystander = Map.of("X-Forwarded-Proto", "https", "X-Forwarded-For", "203.0.113.51",
                "X-XSRF-TOKEN", browser.cookies().get("XSRF-TOKEN"));

        for (int i = 0; i < 3; i++) {
            assertEquals(401, browser.send("POST", "/auth/login", body, attacker).statusCode());
        }

        assertEquals(429, browser.send("POST", "/auth/login", body, attacker).statusCode());
        assertEquals(401, browser.send("POST", "/auth/login", body, bystander).statusCode(),
                "another client behind the same proxy is not blocked");
    }

    @Test
    void theHstsHeader_isNeverSentOnAPlainResponse() throws Exception {
        HttpResponse<String> refused = get(new HttpBrowser(port), "/auth/csrf", Map.of());

        assertFalse(refused.headers().firstValue("Strict-Transport-Security").isPresent());
    }
}
