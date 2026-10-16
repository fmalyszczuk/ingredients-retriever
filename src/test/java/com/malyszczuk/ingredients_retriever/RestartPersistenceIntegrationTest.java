package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import com.malyszczuk.ingredients_retriever.support.PostgresTestDatabase;
import com.malyszczuk.ingredients_retriever.support.TestUsers;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of using PostgreSQL: the data is still there after the application is stopped and started again.
 * Each "run" here is a complete, separate application start (new Spring context, new connection pool, Flyway and
 * Hibernate's schema check all run again) against the same database.
 */
@RequiresDocker
class RestartPersistenceIntegrationTest {

    private ConfigurableApplicationContext startApplication() {
        PostgreSQLContainer postgres = PostgresTestDatabase.get();
        // Command-line arguments, not .properties(): those are only defaults, which application.properties overrides.
        return new SpringApplicationBuilder(IngredientsRetrieverApplication.class)
                .web(WebApplicationType.SERVLET) // a real web app, like production; the security setup needs one
                .run(
                        "--server.port=0",
                        "--pantrypal.security.bcrypt-strength=4",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword());
    }

    @Test
    void shoppingListAndRecipes_surviveRestartingTheApplication_repeatedly() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String flour = "flour-" + tag;
        String eggs = "eggs-" + tag;

        // First run: create data.
        try (ConfigurableApplicationContext first = startApplication()) {
            TestUsers.actAs(TestUsers.create(first.getBean(JdbcTemplate.class), "restart-" + tag + "@example.com"));
            ShoppingListService shoppingList = first.getBean(ShoppingListService.class);
            shoppingList.addItem(flour, new BigDecimal("200"), "g");
            shoppingList.addItem(flour, new BigDecimal("1"), "kg");
            shoppingList.addItem(eggs, new BigDecimal("6"), "pcs");
            shoppingList.updateItem(eggs, true, null, null);
            first.getBean(RecipeService.class).addRecipe("Pancakes " + tag, RecipeSource.TEXT, "pancakes " + tag, List.of(
                    new IngredientRequest("milk-" + tag, new BigDecimal("1.250"), "cup"),
                    new IngredientRequest("salt-" + tag, null, null)));
        }

        // Second and third runs: the data must be exactly as left, and survive another start after that too.
        for (int run = 2; run <= 3; run++) {
            try (ConfigurableApplicationContext again = startApplication()) {
                // the same account as before: the data belongs to it, and it was kept too
                TestUsers.actAs(TestUsers.existing(again.getBean(JdbcTemplate.class), "restart-" + tag + "@example.com"));
                List<ShoppingListItem> items = again.getBean(ShoppingListService.class).listItems().stream()
                        .filter(item -> item.getName().endsWith(tag))
                        .toList();
                // flour and eggs added directly, plus milk and salt that the recipe put on the list
                assertEquals(List.of("eggs-" + tag, "flour-" + tag, "milk-" + tag, "salt-" + tag),
                        items.stream().map(ShoppingListItem::getName).sorted().toList(), "run " + run);

                ShoppingListItem flourItem = items.stream().filter(i -> i.getName().equals(flour)).findFirst().orElseThrow();
                assertEquals(0, new BigDecimal("1200").compareTo(flourItem.getQuantity()), "run " + run);
                assertEquals("g", flourItem.getUnit());
                ShoppingListItem eggsItem = items.stream().filter(i -> i.getName().equals(eggs)).findFirst().orElseThrow();
                assertTrue(eggsItem.isPurchased(), "run " + run);

                // Ingredients are loaded lazily; the web layer has a request-scoped session, this test needs a transaction.
                again.getBean(TransactionTemplate.class).executeWithoutResult(status -> {
                    Recipe recipe = again.getBean(RecipeService.class).listRecipes().stream()
                            .filter(r -> r.getTitle().endsWith(tag)).findFirst().orElseThrow();
                    assertEquals(RecipeSource.TEXT, recipe.getSourceType());
                    assertEquals(2, recipe.getIngredients().size());
                    assertEquals(0, new BigDecimal("1.25").compareTo(recipe.getIngredients().stream()
                            .filter(i -> i.getName().startsWith("milk")).findFirst().orElseThrow().getQuantity()));
                });
            }
        }
    }

    @Test
    void startingTheApplicationAgain_doesNotReapplyOrChangeTheSchema() {
        int migrationsBefore;
        try (ConfigurableApplicationContext first = startApplication()) {
            migrationsBefore = first.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);
        }

        try (ConfigurableApplicationContext again = startApplication()) {
            int migrationsAfter = again.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);
            assertEquals(migrationsBefore, migrationsAfter);
        }
    }
}
