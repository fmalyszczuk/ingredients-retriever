package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.agent.ConversationStore;
import com.malyszczuk.ingredients_retriever.agent.OllamaMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Changing the password and deleting the account, as a browser would do it against a real server. */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pantrypal.security.bcrypt-strength=4",
        "pantrypal.security.throttle.login-failures-per-address-and-account=3"})
class AccountManagementIntegrationTest extends PostgresIntegrationTest {

    private static final String OLD = "the old passphrase";
    private static final String NEW = "a brand new passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConversationStore conversationStore;

    @BeforeEach
    void emptyDatabase() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
    }

    private String credentials(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private HttpBrowser register(String email) throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.get("/auth/csrf");
        assertEquals(201, browser.sendWithCsrf("POST", "/auth/register", credentials(email, OLD)).statusCode());
        return browser;
    }

    private HttpBrowser logIn(String email, String password) throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.get("/auth/csrf");
        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", credentials(email, password)).statusCode());
        return browser;
    }

    private HttpResponse<String> changePassword(HttpBrowser browser, String current, String next) throws Exception {
        return browser.sendWithCsrf("POST", "/auth/password", "{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}");
    }

    private HttpResponse<String> deleteAccount(HttpBrowser browser, String password) throws Exception {
        return browser.sendWithCsrf("POST", "/auth/delete-account", "{\"password\": \"" + password + "\"}");
    }

    // ---------------------------------------------------------------- changing the password

    @Test
    void changingThePassword_makesTheNewOneWork_andTheOldOneStop() throws Exception {
        register("alice@example.com");
        HttpBrowser alice = logIn("alice@example.com", OLD);

        assertEquals(204, changePassword(alice, OLD, NEW).statusCode());

        HttpBrowser attempt = new HttpBrowser(port);
        attempt.get("/auth/csrf");
        assertEquals(401, attempt.sendWithCsrf("POST", "/auth/login", credentials("alice@example.com", OLD)).statusCode());
        logIn("alice@example.com", NEW);
        assertFalse(jdbc.queryForObject("SELECT password_hash FROM users", String.class).contains(NEW));
    }

    @Test
    void changingThePassword_endsTheOtherSessions_butNotTheOneThatDidIt() throws Exception {
        register("bob@example.com");
        HttpBrowser laptop = logIn("bob@example.com", OLD);
        HttpBrowser phone = logIn("bob@example.com", OLD);
        HttpBrowser stolen = logIn("bob@example.com", OLD);
        assertEquals(200, stolen.get("/auth/me").statusCode());

        assertEquals(204, changePassword(laptop, OLD, NEW).statusCode());

        assertEquals(200, laptop.get("/auth/me").statusCode(), "the session that changed it stays logged in");
        assertEquals(401, phone.get("/auth/me").statusCode());
        assertEquals(401, stolen.get("/auth/me").statusCode(), "whoever else was logged in is out");
    }

    @Test
    void changingThePassword_needsTheCurrentOne_andChangesNothingWithoutIt() throws Exception {
        register("carol@example.com");
        HttpBrowser carol = logIn("carol@example.com", OLD);

        HttpResponse<String> response = changePassword(carol, "not the password", NEW);

        assertEquals(400, response.statusCode(), "400 and not 401: carol is still logged in");
        assertEquals("The password is incorrect", response.body());
        assertEquals(200, carol.get("/auth/me").statusCode());
        logIn("carol@example.com", OLD);
    }

    @Test
    void changingThePassword_appliesThePasswordPolicy_andRefusesTheSamePassword() throws Exception {
        register("dave@example.com");
        HttpBrowser dave = logIn("dave@example.com", OLD);

        assertEquals(400, changePassword(dave, OLD, "short").statusCode());
        HttpResponse<String> same = changePassword(dave, OLD, OLD);
        assertEquals(400, same.statusCode());
        assertTrue(same.body().contains("different"), same.body());
        logIn("dave@example.com", OLD);
    }

    @Test
    void guessingThePasswordThroughTheChangeForm_hitsTheSameLimitsAsLogin() throws Exception {
        register("erin@example.com");
        HttpBrowser erin = logIn("erin@example.com", OLD);
        String hashBefore = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        for (int i = 0; i < 3; i++) {
            assertEquals(400, changePassword(erin, "wrong wrong wrong " + i, NEW).statusCode());
        }

        HttpResponse<String> blocked = changePassword(erin, OLD, NEW);

        assertEquals(429, blocked.statusCode(), "even the right password is refused while blocked");
        assertEquals(hashBefore, jdbc.queryForObject("SELECT password_hash FROM users", String.class), "nothing was changed");
    }

    @Test
    void changingThePassword_needsLogin_andACsrfToken() throws Exception {
        register("frank@example.com");
        HttpBrowser anonymous = new HttpBrowser(port);
        anonymous.get("/auth/csrf");
        assertEquals(401, changePassword(anonymous, OLD, NEW).statusCode());

        HttpBrowser frank = logIn("frank@example.com", OLD);
        HttpResponse<String> noToken = frank.send("POST", "/auth/password",
                "{\"currentPassword\": \"" + OLD + "\", \"newPassword\": \"" + NEW + "\"}", java.util.Map.of());
        assertEquals(403, noToken.statusCode());
    }

    // ---------------------------------------------------------------- deleting the account

    @Test
    void deletingTheAccount_removesTheUser_theirData_andEverySession() throws Exception {
        register("gina@example.com");
        HttpBrowser laptop = logIn("gina@example.com", OLD);
        HttpBrowser phone = logIn("gina@example.com", OLD);
        HttpBrowser other = register("hank@example.com");
        other = logIn("hank@example.com", OLD);
        assertEquals(201, laptop.sendWithCsrf("POST", "/recipes",
                "{\"title\": \"Pancakes\", \"ingredients\": [{\"name\": \"flour\", \"quantity\": 200, \"unit\": \"g\"}]}").statusCode());
        assertEquals(201, other.sendWithCsrf("POST", "/shopping-list/items", "{\"name\": \"saffron\", \"quantity\": 1, \"unit\": \"g\"}").statusCode());

        HttpResponse<String> response = deleteAccount(laptop, OLD);

        assertEquals(204, response.statusCode());
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(c -> c.startsWith("PANTRYPAL_SESSION=") && c.toLowerCase().contains("max-age=0")),
                "the session cookie is expired: " + response.headers().allValues("Set-Cookie"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'gina@example.com'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM recipes", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredients", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items WHERE name = 'flour'", Integer.class));
        assertEquals(401, laptop.get("/auth/me").statusCode());
        assertEquals(401, phone.get("/auth/me").statusCode(), "other devices are logged out too");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = 'gina@example.com'", Integer.class));

        // somebody else's account and data are untouched
        assertEquals(200, other.get("/auth/me").statusCode());
        assertTrue(other.get("/shopping-list").body().contains("saffron"));
    }

    @Test
    void aDeletedAccount_cannotLogInAgain_andItsEmailCanBeUsedAgain() throws Exception {
        HttpBrowser gone = register("ivy@example.com");
        gone = logIn("ivy@example.com", OLD);
        assertEquals(204, deleteAccount(gone, OLD).statusCode());

        HttpBrowser attempt = new HttpBrowser(port);
        attempt.get("/auth/csrf");
        assertEquals(401, attempt.sendWithCsrf("POST", "/auth/login", credentials("ivy@example.com", OLD)).statusCode());
        register("ivy@example.com");
    }

    @Test
    void deletingTheAccount_needsThePassword() throws Exception {
        register("jack@example.com");
        HttpBrowser jack = logIn("jack@example.com", OLD);

        HttpResponse<String> response = deleteAccount(jack, "not the password");

        assertEquals(400, response.statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'jack@example.com'", Integer.class));
        assertEquals(200, jack.get("/auth/me").statusCode());
    }

    @Test
    void deletingTheAccount_needsLogin() throws Exception {
        register("kate@example.com");
        HttpBrowser anonymous = new HttpBrowser(port);
        anonymous.get("/auth/csrf");

        assertEquals(401, deleteAccount(anonymous, OLD).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'kate@example.com'", Integer.class));
    }

    @Test
    void deletingTheAccount_forgetsItsChats() throws Exception {
        register("liam@example.com");
        HttpBrowser liam = logIn("liam@example.com", OLD);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = 'liam@example.com'", Long.class);
        conversationStore.append(id + ":c1", "hello", "hi");
        conversationStore.append("999999:c1", "someone else", "hi");

        assertEquals(204, deleteAccount(liam, OLD).statusCode());

        assertTrue(conversationStore.history(id + ":c1").isEmpty());
        java.util.List<OllamaMessage> others = conversationStore.history("999999:c1");
        assertEquals(2, others.size(), "other users' chats are untouched");
    }
}
