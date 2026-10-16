package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Guessing passwords and mass sign-ups get stopped, on the real login endpoint. The limits are set low here (3 failures
 * for one address and account, 5 for one address, 4 for one account; 2 sign-ups); the defaults are in AuthThrottle.
 * Each test uses addresses of its own, since the counters live as long as the application does.
 */
@RequiresDocker
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "pantrypal.security.bcrypt-strength=4",
        "pantrypal.security.throttle.login-failures-per-address-and-account=3",
        "pantrypal.security.throttle.login-failures-per-address=5",
        "pantrypal.security.throttle.login-failures-per-account=4",
        "pantrypal.security.throttle.registrations-per-address=2"})
class BruteForceProtectionIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyDatabase() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    private MockHttpServletResponse login(String address, String email, String password) throws Exception {
        return send("/auth/login", address, "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
    }

    private MockHttpServletResponse register(String address, String email) throws Exception {
        return send("/auth/register", address, "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}");
    }

    private MockHttpServletResponse send(String path, String address, String body) throws Exception {
        String token = mockMvc.perform(get("/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                });
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void afterTooManyWrongPasswords_evenTheRightPasswordIsRefused_withHowLongToWait() throws Exception {
        accountService.register("victim1@example.com", PASSWORD);
        for (int i = 0; i < 3; i++) {
            assertEquals(401, login("10.1.0.1", "victim1@example.com", "wrong wrong wrong").getStatus());
        }

        MockHttpServletResponse blocked = login("10.1.0.1", "victim1@example.com", PASSWORD);

        assertEquals(429, blocked.getStatus());
        assertEquals("Too many attempts. Try again later.", blocked.getContentAsString());
        assertTrue(Long.parseLong(blocked.getHeader("Retry-After")) > 0);
        assertNull(blocked.getCookie("PANTRYPAL_SESSION"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class), "no session may be created");
    }

    @Test
    void theBlock_looksTheSameForAccountsThatDoNotExist() throws Exception {
        accountService.register("real@example.com", PASSWORD);
        for (int i = 0; i < 3; i++) {
            login("10.2.0.1", "real@example.com", "wrong wrong wrong");
            login("10.2.0.2", "ghost@example.com", "wrong wrong wrong");
        }

        MockHttpServletResponse real = login("10.2.0.1", "real@example.com", "wrong wrong wrong");
        MockHttpServletResponse ghost = login("10.2.0.2", "ghost@example.com", "wrong wrong wrong");

        assertEquals(429, real.getStatus());
        assertEquals(429, ghost.getStatus());
        assertEquals(real.getContentAsString(), ghost.getContentAsString());
    }

    @Test
    void theOwner_canStillLogInFromElsewhere_whileSomeoneElseIsBlockedFromTheirAccount() throws Exception {
        accountService.register("victim3@example.com", PASSWORD);
        for (int i = 0; i < 3; i++) {
            login("10.3.0.66", "victim3@example.com", "wrong wrong wrong");
        }
        assertEquals(429, login("10.3.0.66", "victim3@example.com", "wrong wrong wrong").getStatus());

        assertEquals(200, login("10.3.0.1", "victim3@example.com", PASSWORD).getStatus());
    }

    @Test
    void emailCaseAndSpaces_doNotGiveAGuesserFreshAttempts() throws Exception {
        accountService.register("victim4@example.com", PASSWORD);
        String[] spellings = {"victim4@example.com", "VICTIM4@example.com", " Victim4@Example.com ", "victim4@EXAMPLE.com"};
        for (int i = 0; i < 3; i++) {
            login("10.4.0.1", spellings[i], "wrong wrong wrong");
        }

        assertEquals(429, login("10.4.0.1", spellings[3], "wrong wrong wrong").getStatus());
    }

    @Test
    void oneAddressTryingManyAccounts_getsBlocked() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login("10.5.0.1", "nobody" + i + "@example.com", "wrong wrong wrong").getStatus());
        }

        assertEquals(429, login("10.5.0.1", "nobody-else@example.com", "wrong wrong wrong").getStatus());
        assertEquals(401, login("10.5.0.2", "nobody-else@example.com", "wrong wrong wrong").getStatus());
    }

    @Test
    void manyAddressesTryingOneAccount_getBlockedAtTheAccountLimit() throws Exception {
        accountService.register("victim6@example.com", PASSWORD);
        for (int i = 0; i < 4; i++) {
            assertEquals(401, login("10.6.0." + i, "victim6@example.com", "wrong wrong wrong").getStatus());
        }

        assertEquals(429, login("10.6.0.99", "victim6@example.com", "wrong wrong wrong").getStatus());
    }

    @Test
    void aSuccessfulLogin_resetsTheCountOfThatAddressAndAccount() throws Exception {
        accountService.register("typo7@example.com", PASSWORD);
        for (int round = 0; round < 2; round++) { // 4 failures in all: under the address limit of 5, over the pair limit of 3 if never reset
            login("10.7.0.1", "typo7@example.com", "wrong wrong wrong");
            login("10.7.0.1", "typo7@example.com", "wrong wrong wrong");
            assertEquals(200, login("10.7.0.1", "typo7@example.com", PASSWORD).getStatus(), "round " + round);
        }
    }

    @Test
    void signUps_areLimitedPerAddress_evenWhenTheyFail() throws Exception {
        assertEquals(201, register("10.8.0.1", "new8a@example.com").getStatus());
        assertEquals(409, register("10.8.0.1", "new8a@example.com").getStatus());

        MockHttpServletResponse blocked = register("10.8.0.1", "new8b@example.com");

        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'new8b@example.com'", Integer.class));
        assertEquals(201, register("10.8.0.2", "new8b@example.com").getStatus());
    }
}
