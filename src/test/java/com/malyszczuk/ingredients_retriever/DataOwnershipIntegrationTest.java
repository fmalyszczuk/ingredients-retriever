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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two real users on a real server: what one of them owns is invisible and untouchable to the other. */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "pantrypal.security.bcrypt-strength=4")
class DataOwnershipIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpBrowser alice;
    private HttpBrowser bob;

    @BeforeEach
    void twoLoggedInUsers() throws Exception {
        jdbc.update("DELETE FROM users"); // their recipes and shopping lists go with them
        alice = logIn("alice@example.com");
        bob = logIn("bob@example.com");
    }

    private HttpBrowser logIn(String email) throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        String credentials = "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}";
        browser.get("/auth/csrf");
        assertEquals(202, browser.sendWithCsrf("POST", "/auth/register", credentials).statusCode());
        assertEquals(200, browser.sendWithCsrf("POST", "/auth/login", credentials).statusCode());
        return browser;
    }

    private long createRecipe(HttpBrowser who, String title) throws Exception {
        var response = who.sendWithCsrf("POST", "/recipes", "{\"title\": \"" + title
                + "\", \"ingredients\": [{\"name\": \"flour\", \"quantity\": 200, \"unit\": \"g\"}]}");
        assertEquals(201, response.statusCode());
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(response.body());
        assertTrue(id.find(), response.body());
        return Long.parseLong(id.group(1));
    }

    private void addItem(HttpBrowser who, String name, int quantity) throws Exception {
        assertEquals(201, who.sendWithCsrf("POST", "/shopping-list/items",
                "{\"name\": \"" + name + "\", \"quantity\": " + quantity + ", \"unit\": \"g\"}").statusCode());
    }

    @Test
    void theShoppingList_isPrivate() throws Exception {
        addItem(alice, "saffron", 1);

        assertTrue(alice.get("/shopping-list").body().contains("saffron"));
        assertEquals("[]", bob.get("/shopping-list").body().replaceAll("\\s", ""));
    }

    @Test
    void theSameItemName_isTwoIndependentItems() throws Exception {
        addItem(alice, "flour", 100);
        addItem(bob, "flour", 5);
        addItem(bob, "flour", 5);

        assertTrue(alice.get("/shopping-list").body().contains("100"), alice.get("/shopping-list").body());
        assertFalse(alice.get("/shopping-list").body().contains("110"));
        assertTrue(bob.get("/shopping-list").body().contains("10"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
    }

    @Test
    void anotherUsersItems_cannotBeChanged_orRemoved_orCleared() throws Exception {
        addItem(alice, "saffron", 1);

        assertEquals(404, bob.sendWithCsrf("PATCH", "/shopping-list/items/saffron", "{\"purchased\": true}").statusCode());
        bob.sendWithCsrf("DELETE", "/shopping-list/items/saffron", null);
        bob.sendWithCsrf("DELETE", "/shopping-list", null);

        assertTrue(alice.get("/shopping-list").body().contains("saffron"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items WHERE NOT purchased", Integer.class));
    }

    @Test
    void recipes_arePrivate_andLookJustLikeTheyDoNotExistToOthers() throws Exception {
        long aliceRecipe = createRecipe(alice, "Alice's Pancakes");

        assertTrue(alice.get("/recipes").body().contains("Alice's Pancakes"));
        assertEquals("[]", bob.get("/recipes").body().replaceAll("\\s", ""));
        assertEquals(200, alice.get("/recipes/" + aliceRecipe).statusCode());
        // 404, not 403: nothing reveals that the id exists
        assertEquals(404, bob.get("/recipes/" + aliceRecipe).statusCode());
        assertEquals(404, bob.sendWithCsrf("DELETE", "/recipes/" + aliceRecipe, null).statusCode());
        assertEquals(404, bob.sendWithCsrf("DELETE", "/recipes/" + aliceRecipe + "?removeFromShoppingList=true", null).statusCode());

        assertEquals(200, alice.get("/recipes/" + aliceRecipe).statusCode());
        assertTrue(alice.get("/shopping-list").body().contains("flour"), "her list must be untouched too");
    }

    @Test
    void addingARecipe_putsItsIngredientsOnTheOwnersListOnly() throws Exception {
        createRecipe(alice, "Pancakes");

        assertTrue(alice.get("/shopping-list").body().contains("flour"));
        assertEquals("[]", bob.get("/shopping-list").body().replaceAll("\\s", ""));
    }

    @Test
    void deletingAnAccount_removesItsData_andNothingElse() {
        jdbc.update("INSERT INTO shopping_list_items (user_id, name, purchased) SELECT id, 'x', false FROM users");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));

        jdbc.update("DELETE FROM users WHERE email = 'alice@example.com'");

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
    }
}
