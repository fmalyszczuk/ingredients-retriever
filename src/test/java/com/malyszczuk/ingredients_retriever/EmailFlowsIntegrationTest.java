package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.mail.Mailer;
import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifying an email address and resetting a forgotten password, end to end on a real server. The emails are caught by a
 * test mailer, and the tokens are taken out of them, exactly as a user would take them from their inbox.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pantrypal.security.bcrypt-strength=4",
        "pantrypal.security.require-verified-email=true",
        "pantrypal.security.throttle.mail-requests-per-address=1000",
        "pantrypal.security.throttle.mail-requests-per-account=3",
        "pantrypal.public-url=https://app.example.com"})
class EmailFlowsIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";
    private static final String NEW_PASSWORD = "an even better passphrase";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    record Mail(String to, String subject, String body) {
        String token() {
            Matcher matcher = TOKEN.matcher(body);
            assertTrue(matcher.find(), "no link in: " + body);
            return matcher.group(1);
        }
    }

    private static final BlockingQueue<Mail> SENT = new LinkedBlockingQueue<>();
    private static final AtomicBoolean MAIL_SERVER_DOWN = new AtomicBoolean();

    @TestConfiguration
    static class CapturingMail {
        @Bean
        @Primary
        Mailer capturingMailer() {
            return (to, subject, body) -> {
                if (MAIL_SERVER_DOWN.get()) {
                    throw new IllegalStateException("smtp is down");
                }
                SENT.add(new Mail(to, subject, body));
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        MAIL_SERVER_DOWN.set(false);
        SENT.clear();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    // ---------------------------------------------------------------- helpers

    private HttpBrowser browser() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.get("/auth/csrf");
        return browser;
    }

    private String credentials(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private Mail nextMail() throws Exception {
        Mail mail = SENT.poll(10, TimeUnit.SECONDS);
        assertNotNull(mail, "an email should have been sent");
        return mail;
    }

    private void assertNoMail() throws Exception {
        assertNull(SENT.poll(700, TimeUnit.MILLISECONDS), "no email should have been sent");
    }

    private Mail register(String email) throws Exception {
        assertEquals(201, browser().sendWithCsrf("POST", "/auth/register", credentials(email, PASSWORD)).statusCode());
        return nextMail();
    }

    private HttpResponse<String> login(HttpBrowser browser, String email, String password) throws Exception {
        return browser.sendWithCsrf("POST", "/auth/login", credentials(email, password));
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return browser().sendWithCsrf("POST", path, json);
    }

    private HttpResponse<String> verify(String token) throws Exception {
        return post("/auth/verify-email", "{\"token\": \"" + token + "\"}");
    }

    private HttpResponse<String> forgot(String email) throws Exception {
        return post("/auth/forgot-password", "{\"email\": \"" + email + "\"}");
    }

    private HttpResponse<String> reset(String token, String password) throws Exception {
        return post("/auth/reset-password", "{\"token\": \"" + token + "\", \"newPassword\": \"" + password + "\"}");
    }

    /** A registered, verified account. */
    private void verifiedAccount(String email) throws Exception {
        assertEquals(204, verify(register(email).token()).statusCode());
    }

    // ---------------------------------------------------------------- verifying the address

    @Test
    void registering_emailsALink_andLoginWaitsUntilItHasBeenOpened() throws Exception {
        Mail mail = register("alice@example.com");

        assertEquals("alice@example.com", mail.to());
        assertTrue(mail.body().contains("https://app.example.com/verify-email?token="), mail.body());

        HttpBrowser browser = browser();
        HttpResponse<String> refused = login(browser, "alice@example.com", PASSWORD);
        assertEquals(403, refused.statusCode());
        assertEquals("Email address not verified", refused.body());
        assertEquals(401, browser.get("/auth/me").statusCode(), "no session may start");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class));
        assertEquals(401, login(browser, "alice@example.com", "wrong wrong wrong").statusCode(),
                "a wrong password is still just a wrong password");

        assertEquals(204, verify(mail.token()).statusCode());

        assertEquals(200, login(browser, "alice@example.com", PASSWORD).statusCode());
        assertEquals(200, browser.get("/auth/me").statusCode());
    }

    @Test
    void aVerificationLink_worksOnce() throws Exception {
        String token = register("bob@example.com").token();

        assertEquals(204, verify(token).statusCode());
        HttpResponse<String> again = verify(token);

        assertEquals(400, again.statusCode());
        assertEquals("The link is invalid or has expired", again.body());
    }

    @Test
    void wrongExpiredAndMistypedLinks_allGetTheSameAnswer() throws Exception {
        String token = register("carol@example.com").token();
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 minute'");

        HttpResponse<String> expired = verify(token);
        HttpResponse<String> made_up = verify("A".repeat(43));
        HttpResponse<String> truncated = verify(token.substring(1));

        for (HttpResponse<String> response : java.util.List.of(expired, made_up, truncated)) {
            assertEquals(400, response.statusCode());
            assertEquals("The link is invalid or has expired", response.body());
        }
        assertEquals(400, post("/auth/verify-email", "{\"token\": \"" + "x".repeat(500) + "\"}").statusCode());
        assertEquals(403, login(browser(), "carol@example.com", PASSWORD).statusCode(), "still not verified");
    }

    @Test
    void aPasswordResetLink_cannotVerifyAnAddress_andTheOtherWayRound() throws Exception {
        register("dave@example.com");
        forgot("dave@example.com");
        String resetToken = nextMail().token();

        assertEquals(400, verify(resetToken).statusCode());
        assertEquals(400, reset(register("dave2@example.com").token(), NEW_PASSWORD).statusCode());
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        String token = register("erin@example.com").token();

        String stored = jdbc.queryForObject("SELECT token_hash FROM account_tokens", String.class);

        assertNotEquals(token, stored);
        assertEquals(64, stored.length());
        assertFalse(stored.contains(token));
    }

    @Test
    void resendingTheLink_killsTheOldOne_andIsSilentAboutWhoHasAnAccount() throws Exception {
        String first = register("frank@example.com").token();
        HttpResponse<String> unknown = post("/auth/resend-verification", "{\"email\": \"nobody1@example.com\"}");
        assertNoMail();

        HttpResponse<String> known = post("/auth/resend-verification", "{\"email\": \"Frank@Example.com\"}");
        String second = nextMail().token();

        assertEquals(204, unknown.statusCode());
        assertEquals(204, known.statusCode());
        assertEquals(unknown.body(), known.body());
        assertNotEquals(first, second);
        assertEquals(400, verify(first).statusCode(), "the old link is dead");
        assertEquals(204, verify(second).statusCode());
    }

    @Test
    void resendingTheLink_toAVerifiedAccount_sendsNothing() throws Exception {
        verifiedAccount("gina@example.com");

        assertEquals(204, post("/auth/resend-verification", "{\"email\": \"gina@example.com\"}").statusCode());

        assertNoMail();
    }

    @Test
    void emailRequests_areLimitedPerAccount_evenForAccountsThatDoNotExist() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(204, forgot("ghost@example.com").statusCode());
        }

        HttpResponse<String> blocked = forgot("ghost@example.com");

        assertEquals(429, blocked.statusCode());
        assertNotNull(blocked.headers().firstValue("Retry-After").orElse(null));
        assertEquals(429, post("/auth/resend-verification", "{\"email\": \"ghost@example.com\"}").statusCode(),
                "one budget for both kinds of email");
    }

    // ---------------------------------------------------------------- forgotten passwords

    @Test
    void forgottenPassword_theLinkSetsANewPassword_andLogsEveryoneOut() throws Exception {
        verifiedAccount("hank@example.com");
        HttpBrowser laptop = browser();
        assertEquals(200, login(laptop, "hank@example.com", PASSWORD).statusCode());
        HttpBrowser attacker = browser();
        assertEquals(200, login(attacker, "hank@example.com", PASSWORD).statusCode());

        assertEquals(204, forgot("hank@example.com").statusCode());
        Mail mail = nextMail();
        assertTrue(mail.body().contains("https://app.example.com/reset-password?token="), mail.body());
        assertEquals(204, reset(mail.token(), NEW_PASSWORD).statusCode());

        assertEquals(401, laptop.get("/auth/me").statusCode(), "every session ends, including whoever was logged in");
        assertEquals(401, attacker.get("/auth/me").statusCode());
        assertEquals(401, login(browser(), "hank@example.com", PASSWORD).statusCode(), "the old password is gone");
        assertEquals(200, login(browser(), "hank@example.com", NEW_PASSWORD).statusCode());
        assertFalse(jdbc.queryForObject("SELECT password_hash FROM users", String.class).contains(NEW_PASSWORD));
    }

    @Test
    void forgottenPassword_looksTheSameWhetherOrNotTheAccountExists() throws Exception {
        verifiedAccount("ivy@example.com");

        HttpResponse<String> known = forgot("ivy@example.com");
        nextMail();
        HttpResponse<String> unknown = forgot("nobody2@example.com");

        assertEquals(known.statusCode(), unknown.statusCode());
        assertEquals(known.body(), unknown.body());
        assertNoMail();
    }

    @Test
    void aResetLink_worksOnce() throws Exception {
        verifiedAccount("jack@example.com");
        forgot("jack@example.com");
        String token = nextMail().token();

        assertEquals(204, reset(token, NEW_PASSWORD).statusCode());

        assertEquals(400, reset(token, "yet another passphrase").statusCode());
        assertEquals(200, login(browser(), "jack@example.com", NEW_PASSWORD).statusCode());
    }

    @Test
    void aWeakNewPassword_isRefused_withoutSpendingTheLink() throws Exception {
        verifiedAccount("kate@example.com");
        forgot("kate@example.com");
        String token = nextMail().token();

        HttpResponse<String> weak = reset(token, "short");

        assertEquals(400, weak.statusCode());
        assertFalse(weak.body().contains("link"), weak.body());
        assertEquals(204, reset(token, NEW_PASSWORD).statusCode());
    }

    @Test
    void aNewResetRequest_killsTheEarlierLink() throws Exception {
        verifiedAccount("liam@example.com");
        forgot("liam@example.com");
        String first = nextMail().token();
        forgot("liam@example.com");
        String second = nextMail().token();

        assertEquals(400, reset(first, NEW_PASSWORD).statusCode());
        assertEquals(204, reset(second, NEW_PASSWORD).statusCode());
    }

    @Test
    void changingThePassword_killsResetLinksSentBefore() throws Exception {
        verifiedAccount("mia@example.com");
        forgot("mia@example.com");
        String token = nextMail().token();
        HttpBrowser mia = browser();
        login(mia, "mia@example.com", PASSWORD);
        assertEquals(204, mia.sendWithCsrf("POST", "/auth/password",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}").statusCode());

        assertEquals(400, reset(token, "yet another passphrase").statusCode());
        assertEquals(200, login(browser(), "mia@example.com", NEW_PASSWORD).statusCode());
    }

    @Test
    void resettingThePassword_alsoProvesTheAddress() throws Exception {
        register("noah@example.com"); // never opens the verification link
        forgot("noah@example.com");

        assertEquals(204, reset(nextMail().token(), NEW_PASSWORD).statusCode());

        assertEquals(200, login(browser(), "noah@example.com", NEW_PASSWORD).statusCode());
    }

    @Test
    void aBrokenMailServer_doesNotChangeWhatTheCallerSees() throws Exception {
        verifiedAccount("olga@example.com");
        MAIL_SERVER_DOWN.set(true);

        HttpResponse<String> known = forgot("olga@example.com");
        HttpResponse<String> unknown = forgot("nobody3@example.com");

        assertEquals(204, known.statusCode());
        assertEquals(204, unknown.statusCode());
        assertEquals(known.body(), unknown.body());
    }

    // ---------------------------------------------------------------- the rest

    @Test
    void theEmailEndpoints_areOpen_butNeedACsrfToken() throws Exception {
        HttpBrowser noToken = new HttpBrowser(port);

        for (String path : new String[]{"/auth/verify-email", "/auth/resend-verification", "/auth/forgot-password", "/auth/reset-password"}) {
            assertEquals(403, noToken.send("POST", path, "{}", java.util.Map.of()).statusCode(), path);
        }
        assertEquals(204, post("/auth/forgot-password", "{\"email\": \"nobody4@example.com\"}").statusCode(), "open to anonymous users");
    }
}
