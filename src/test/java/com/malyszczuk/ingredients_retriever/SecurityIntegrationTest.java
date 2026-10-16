package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The real security filter chain on the real application, with a real database: nobody gets anything without logging
 * in, login and logout behave, and the CSRF protection is on. (Cookie flags and session ids need a real server; see
 * {@link AuthOverHttpIntegrationTest}.)
 */
@RequiresDocker
@AutoConfigureMockMvc
class SecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyDatabase() {
        jdbc.update("DELETE FROM ingredients");
        jdbc.update("DELETE FROM recipes");
        jdbc.update("DELETE FROM shopping_list_items");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    // ---------------------------------------------------------------- helpers

    /** A CSRF token, obtained the way the frontend does: from the cookie that GET /auth/csrf sets. */
    private String csrfToken() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse();
        assertEquals(204, response.getStatus());
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "GET /auth/csrf must set the XSRF-TOKEN cookie");
        return cookie.getValue();
    }

    private RequestBuilder withCsrf(AbstractMockHttpServletRequestBuilder<?> request, String token) {
        request.cookie(new Cookie("XSRF-TOKEN", token));
        request.header("X-XSRF-TOKEN", token);
        return request;
    }

    /** Logs in and returns the session cookie, which is all a browser keeps. Sessions are stored in PostgreSQL. */
    private Cookie logIn(String email, String password) throws Exception {
        return logIn(email, password, null);
    }

    private Cookie logIn(String email, String password, Cookie existingSession) throws Exception {
        String token = csrfToken();
        MockHttpServletRequestBuilder login = post("/auth/login").contentType(JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
        if (existingSession != null) {
            login.cookie(existingSession);
        }
        MockHttpServletResponse response = mockMvc.perform(withCsrf(login, token)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), "login should succeed");
        String header = sessionCookieHeader(response);
        assertNotNull(header, "login must set a session cookie");
        int end = header.indexOf(';') > 0 ? header.indexOf(';') : header.length();
        int equals = header.indexOf('=');
        return new Cookie(header.substring(0, equals), header.substring(equals + 1, end));
    }

    /**
     * The Set-Cookie header that starts a session (anything but the CSRF cookie), or null. Read from the raw header,
     * because the session cookie is written as a header and response.getCookie() would be null whether or not one was
     * set, which would make a "no session cookie" assertion pass for the wrong reason. MockMvc has no real web server,
     * so the cookie is named SESSION here; its real name and flags (PANTRYPAL_SESSION, HttpOnly, SameSite) are checked
     * in AuthOverHttpIntegrationTest.
     */
    private String sessionCookieHeader(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream().filter(h -> !h.startsWith("XSRF-TOKEN=")).findFirst().orElse(null);
    }

    private record Endpoint(HttpMethod method, String path, String body) {
        AbstractMockHttpServletRequestBuilder<?> request() {
            if (path.equals("/recipes/from-file")) {
                return multipart(path).file(new MockMultipartFile("file", "recipe.txt", "text/plain", "2 eggs".getBytes()));
            }
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
            return body == null ? builder : builder.contentType(JSON).content(body);
        }

        boolean changesData() {
            return method != HttpMethod.GET;
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** Every endpoint the application has, plus things that don't exist, which must not reveal that they don't. */
    private static List<Endpoint> everyEndpoint() {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new Endpoint(HttpMethod.GET, "/shopping-list", null));
        endpoints.add(new Endpoint(HttpMethod.POST, "/shopping-list/items", "{\"name\": \"eggs\", \"quantity\": 2, \"unit\": \"pcs\"}"));
        endpoints.add(new Endpoint(HttpMethod.PATCH, "/shopping-list/items/eggs", "{\"purchased\": true}"));
        endpoints.add(new Endpoint(HttpMethod.DELETE, "/shopping-list/items/eggs", null));
        endpoints.add(new Endpoint(HttpMethod.DELETE, "/shopping-list", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/recipes", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/recipes/1", null));
        endpoints.add(new Endpoint(HttpMethod.POST, "/recipes", "{\"title\": \"x\", \"ingredients\": [{\"name\": \"a\"}]}"));
        endpoints.add(new Endpoint(HttpMethod.DELETE, "/recipes/1", null));
        endpoints.add(new Endpoint(HttpMethod.POST, "/recipes/from-url", "{\"url\": \"https://example.com/r\"}"));
        endpoints.add(new Endpoint(HttpMethod.POST, "/recipes/from-text", "{\"text\": \"pancakes\"}"));
        endpoints.add(new Endpoint(HttpMethod.POST, "/recipes/from-file", null));
        endpoints.add(new Endpoint(HttpMethod.POST, "/chat", "{\"message\": \"hi\"}"));
        endpoints.add(new Endpoint(HttpMethod.DELETE, "/chat/abc", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/auth/me", null));
        endpoints.add(new Endpoint(HttpMethod.POST, "/auth/password", "{\"currentPassword\": \"x\", \"newPassword\": \"y\"}"));
        endpoints.add(new Endpoint(HttpMethod.POST, "/auth/delete-account", "{\"password\": \"x\"}"));
        endpoints.add(new Endpoint(HttpMethod.GET, "/", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/error", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/does-not-exist", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/actuator/health", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/h2-console", null));
        endpoints.add(new Endpoint(HttpMethod.PUT, "/shopping-list", "{}"));
        endpoints.add(new Endpoint(HttpMethod.POST, "/auth/does-not-exist", "{}"));
        return endpoints;
    }

    // ---------------------------------------------------------------- nobody gets in without logging in

    @Test
    void everyEndpoint_answers401_toAnonymousUsers_evenWithAValidCsrfToken() throws Exception {
        String token = csrfToken();

        for (Endpoint endpoint : everyEndpoint()) {
            MockHttpServletResponse response = mockMvc.perform(withCsrf(endpoint.request(), token)).andReturn().getResponse();

            assertEquals(401, response.getStatus(), endpoint + " must be refused to anonymous users");
            assertEquals("Authentication required", response.getContentAsString(), endpoint.toString());
        }
    }

    @Test
    void everyEndpoint_refusesAnonymousUsers_withoutACsrfTokenToo_andNeverAnswersWithData() throws Exception {
        for (Endpoint endpoint : everyEndpoint()) {
            MockHttpServletResponse response = mockMvc.perform(endpoint.request()).andReturn().getResponse();

            // Changes are stopped by the CSRF check before the login check does anything (403); reads by the login check (401).
            assertEquals(endpoint.changesData() ? 403 : 401, response.getStatus(), endpoint + " got " + response.getStatus());
        }
    }

    @Test
    void anAnonymousRequestChangesNothing() throws Exception {
        String token = csrfToken();

        mockMvc.perform(withCsrf(post("/shopping-list/items").contentType(JSON).content("{\"name\": \"eggs\", \"quantity\": 2}"), token));
        mockMvc.perform(withCsrf(post("/recipes").contentType(JSON).content("{\"title\": \"x\", \"ingredients\": [{\"name\": \"a\"}]}"), token));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM recipes", Integer.class));
    }

    @Test
    void aBogusSessionOrCookie_isNoBetterThanNone() throws Exception {
        String token = csrfToken();

        MockHttpServletResponse response = mockMvc.perform(withCsrf(get("/shopping-list")
                        .cookie(new Cookie("PANTRYPAL_SESSION", "not-a-real-session")), token))
                .andReturn().getResponse();

        assertEquals(401, response.getStatus());
    }

    @Test
    void theOnlyOpenDoors_areRegisteringLoggingInAndFetchingACsrfToken() throws Exception {
        String token = csrfToken();

        // reachable without being logged in (these would be 401 if they were protected)
        assertEquals(400, mockMvc.perform(withCsrf(post("/auth/register").contentType(JSON).content("{}"), token)).andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(withCsrf(post("/auth/login").contentType(JSON).content("{}"), token)).andReturn().getResponse().getStatus());
        assertEquals(204, mockMvc.perform(get("/auth/csrf")).andReturn().getResponse().getStatus());
        // and only with the right method
        assertEquals(401, mockMvc.perform(get("/auth/register")).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/auth/login")).andReturn().getResponse().getStatus());
    }

    // ---------------------------------------------------------------- registering and logging in

    @Test
    void register_thenLogin_givesAccessToTheApplication() throws Exception {
        String token = csrfToken();
        assertEquals(201, mockMvc.perform(withCsrf(post("/auth/register").contentType(JSON)
                        .content("{\"email\": \"alice@example.com\", \"password\": \"" + PASSWORD + "\"}"), token))
                .andReturn().getResponse().getStatus());

        Cookie session = logIn("alice@example.com", PASSWORD);

        assertEquals(200, mockMvc.perform(get("/shopping-list").cookie(session)).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(get("/recipes").cookie(session)).andReturn().getResponse().getStatus());
        assertEquals(201, mockMvc.perform(withCsrf(post("/shopping-list/items").cookie(session).contentType(JSON)
                        .content("{\"name\": \"eggs\", \"quantity\": 2, \"unit\": \"pcs\"}"), token))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void login_returnsTheUser_andNothingSecret() throws Exception {
        accountService.register("bob@example.com", PASSWORD);
        String token = csrfToken();

        MockHttpServletResponse response = mockMvc.perform(withCsrf(post("/auth/login").contentType(JSON)
                        .content("{\"email\": \"bob@example.com\", \"password\": \"" + PASSWORD + "\"}"), token))
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertEquals(200, response.getStatus());
        assertTrue(body.contains("bob@example.com"));
        assertFalse(body.toLowerCase().contains("password"), body);
        assertFalse(body.contains("bcrypt"), body);
    }

    @Test
    void login_isCaseInsensitiveForTheEmail_andIgnoresSurroundingSpaces() throws Exception {
        accountService.register("carol@example.com", PASSWORD);

        logIn("CAROL@Example.com", PASSWORD);
        logIn("  carol@example.com ", PASSWORD);
    }

    @Test
    void login_failsTheSameWay_forAWrongPassword_anUnknownEmail_andADisabledAccount() throws Exception {
        accountService.register("dave@example.com", PASSWORD);
        accountService.register("erin@example.com", PASSWORD);
        jdbc.update("UPDATE users SET enabled = false WHERE email = 'erin@example.com'");
        String token = csrfToken();
        List<MockHttpServletResponse> failures = new ArrayList<>();

        for (String[] attempt : new String[][]{
                {"dave@example.com", "the wrong passphrase"},
                {"nobody@example.com", PASSWORD},
                {"erin@example.com", PASSWORD}}) {
            failures.add(mockMvc.perform(withCsrf(post("/auth/login").contentType(JSON)
                            .content("{\"email\": \"" + attempt[0] + "\", \"password\": \"" + attempt[1] + "\"}"), token))
                    .andReturn().getResponse());
        }

        for (MockHttpServletResponse failure : failures) {
            assertEquals(401, failure.getStatus());
            assertEquals("Invalid email or password", failure.getContentAsString());
            assertNull(sessionCookieHeader(failure));
        }
    }

    @Test
    void aFailedLogin_doesNotLogAnyoneIn_andCreatesNoSession() throws Exception {
        accountService.register("frank@example.com", PASSWORD);
        String token = csrfToken();

        MockHttpServletResponse response = mockMvc.perform(withCsrf(post("/auth/login").contentType(JSON)
                .content("{\"email\": \"frank@example.com\", \"password\": \"wrong wrong wrong\"}"), token)).andReturn().getResponse();

        assertEquals(401, response.getStatus());
        assertNull(sessionCookieHeader(response));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class));
    }

    @Test
    void login_needsACsrfToken() throws Exception {
        accountService.register("grace@example.com", PASSWORD);

        MockHttpServletResponse response = mockMvc.perform(post("/auth/login").contentType(JSON)
                        .content("{\"email\": \"grace@example.com\", \"password\": \"" + PASSWORD + "\"}"))
                .andReturn().getResponse();

        assertEquals(403, response.getStatus());
        assertEquals("Missing or invalid CSRF token", response.getContentAsString());
    }

    @Test
    void login_startsAFreshSession_soAnIdFromBeforeTheLoginStopsWorking() throws Exception {
        accountService.register("heidi@example.com", PASSWORD);
        Cookie before = logIn("heidi@example.com", PASSWORD);
        assertEquals(200, mockMvc.perform(get("/auth/me").cookie(before)).andReturn().getResponse().getStatus());

        Cookie after = logIn("heidi@example.com", PASSWORD, before);

        assertNotEquals(before.getValue(), after.getValue(), "the session id must change on login");
        assertEquals(401, mockMvc.perform(get("/auth/me").cookie(before)).andReturn().getResponse().getStatus(),
                "the old id must be dead, or a planted id would stay valid after the victim logs in");
        assertEquals(200, mockMvc.perform(get("/auth/me").cookie(after)).andReturn().getResponse().getStatus());
    }

    @Test
    void theStoredSession_belongsToTheUser_andNeverContainsThePasswordHash() throws Exception {
        accountService.register("ivan@example.com", PASSWORD);
        logIn("ivan@example.com", PASSWORD);

        assertEquals("ivan@example.com", jdbc.queryForObject("SELECT principal_name FROM spring_session", String.class));
        byte[] stored = jdbc.queryForObject(
                "SELECT attribute_bytes FROM spring_session_attributes WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'", byte[].class);
        String serialized = new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(serialized.contains("ivan@example.com"), "looking at the right bytes: the user's email is in there");
        assertFalse(serialized.contains("$2a$") || serialized.contains("{bcrypt}"), "the password hash must never be stored in the session");
        assertFalse(serialized.contains(PASSWORD));
    }

    // ---------------------------------------------------------------- who am I, and logging out

    @Test
    void me_returnsTheLoggedInUser_and401WhenNobodyIs() throws Exception {
        accountService.register("judy@example.com", PASSWORD);
        Cookie session = logIn("judy@example.com", PASSWORD);

        MockHttpServletResponse loggedIn = mockMvc.perform(get("/auth/me").cookie(session)).andReturn().getResponse();
        MockHttpServletResponse anonymous = mockMvc.perform(get("/auth/me")).andReturn().getResponse();

        assertEquals(200, loggedIn.getStatus());
        assertTrue(loggedIn.getContentAsString().contains("judy@example.com"));
        assertEquals(401, anonymous.getStatus());
    }

    @Test
    void logout_endsTheSession_forGood() throws Exception {
        accountService.register("kate@example.com", PASSWORD);
        Cookie session = logIn("kate@example.com", PASSWORD);
        String token = csrfToken();
        assertEquals(200, mockMvc.perform(get("/shopping-list").cookie(session)).andReturn().getResponse().getStatus());

        int status = mockMvc.perform(withCsrf(post("/auth/logout").cookie(session), token)).andReturn().getResponse().getStatus();

        assertEquals(204, status);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class),
                "the server-side session must be destroyed, not just forgotten by the client");
        assertEquals(401, mockMvc.perform(get("/shopping-list").cookie(session)).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/auth/me").cookie(session)).andReturn().getResponse().getStatus());
    }

    @Test
    void logout_needsACsrfToken_soAnotherSiteCannotLogYouOut() throws Exception {
        accountService.register("leo@example.com", PASSWORD);
        Cookie session = logIn("leo@example.com", PASSWORD);

        assertEquals(403, mockMvc.perform(post("/auth/logout").cookie(session)).andReturn().getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class));
        assertEquals(200, mockMvc.perform(get("/auth/me").cookie(session)).andReturn().getResponse().getStatus());
    }

    @Test
    void logout_isHarmless_whenNobodyIsLoggedIn() throws Exception {
        assertEquals(204, mockMvc.perform(withCsrf(post("/auth/logout"), csrfToken())).andReturn().getResponse().getStatus());
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void changes_needAValidCsrfToken_evenWhenLoggedIn() throws Exception {
        accountService.register("mallory@example.com", PASSWORD);
        Cookie session = logIn("mallory@example.com", PASSWORD);
        String token = csrfToken();
        String body = "{\"name\": \"eggs\", \"quantity\": 1, \"unit\": \"pcs\"}";

        // what a malicious page can do: the browser attaches the session cookie by itself, but it can't add the header
        assertEquals(403, mockMvc.perform(post("/shopping-list/items").cookie(session).contentType(JSON).content(body)).andReturn().getResponse().getStatus());
        // a made-up header that doesn't match the cookie
        assertEquals(403, mockMvc.perform(post("/shopping-list/items").cookie(session).contentType(JSON).content(body)
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", "forged")).andReturn().getResponse().getStatus());
        // a header without the cookie
        assertEquals(403, mockMvc.perform(post("/shopping-list/items").cookie(session).contentType(JSON).content(body)
                .header("X-XSRF-TOKEN", token)).andReturn().getResponse().getStatus());
        // the right way
        assertEquals(201, mockMvc.perform(withCsrf(post("/shopping-list/items").cookie(session).contentType(JSON).content(body), token))
                .andReturn().getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
    }

    @Test
    void readingNeedsNoCsrfToken() throws Exception {
        accountService.register("nina@example.com", PASSWORD);
        Cookie session = logIn("nina@example.com", PASSWORD);

        assertEquals(200, mockMvc.perform(get("/shopping-list").cookie(session)).andReturn().getResponse().getStatus());
    }

    // ---------------------------------------------------------------- what the responses give away

    @Test
    void responses_carrySecurityHeaders_andAreNeverCached() throws Exception {
        accountService.register("oscar@example.com", PASSWORD);
        Cookie session = logIn("oscar@example.com", PASSWORD);

        MockHttpServletResponse response = mockMvc.perform(get("/auth/me").cookie(session)).andReturn().getResponse();

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertTrue(response.getHeader("Cache-Control").contains("no-store"), response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("default-src 'none'"));
    }

    @Test
    void errors_neverShowStackTracesOrExceptionNames() throws Exception {
        accountService.register("peggy@example.com", PASSWORD);
        Cookie session = logIn("peggy@example.com", PASSWORD);

        for (MockHttpServletRequestBuilder request : List.<MockHttpServletRequestBuilder>of(
                get("/does-not-exist").cookie(session),
                get("/recipes/not-a-number").cookie(session),
                get("/recipes/999999").cookie(session))) {
            String body = mockMvc.perform(request).andReturn().getResponse().getContentAsString();

            assertFalse(body.contains("Exception"), body);
            assertFalse(body.contains("at com.malyszczuk"), body);
            assertFalse(body.contains("trace"), body);
        }
    }
}
