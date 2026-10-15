package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shopping list's merging rules and their consistency guarantees, on a real database with real transactions. */
@RequiresDocker
class ShoppingListConsistencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ShoppingListService shoppingListService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.update("DELETE FROM ingredients");
        jdbc.update("DELETE FROM recipes");
        jdbc.update("DELETE FROM shopping_list_items");
    }

    private BigDecimal quantityOf(String name) {
        return jdbc.queryForObject("SELECT quantity FROM shopping_list_items WHERE lower(name) = lower(?)", BigDecimal.class, name);
    }

    private int rows(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM shopping_list_items WHERE lower(name) = lower(?)", Integer.class, name);
    }

    /** Runs the tasks at the same moment, from separate threads, and returns what each one ended with. */
    private <T> List<Object> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentAddsToAnExistingItem_neverLoseAnUpdate() throws Exception {
        shoppingListService.addItem("flour", new BigDecimal("1000"), "g");

        List<Callable<ShoppingListItem>> adds = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            adds.add(() -> shoppingListService.addItem("flour", BigDecimal.ONE, "g"));
        }
        List<Object> outcomes = runConcurrently(adds);

        assertTrue(outcomes.stream().allMatch(o -> o instanceof ShoppingListItem), "every add must succeed: " + outcomes);
        assertEquals(0, new BigDecimal("1024").compareTo(quantityOf("flour")));
        assertEquals(1, rows("flour"));
    }

    @Test
    void concurrentAddsWithDifferentUnits_stillAddUpExactly() throws Exception {
        shoppingListService.addItem("sugar", new BigDecimal("500"), "g");

        List<Callable<ShoppingListItem>> adds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            adds.add(() -> shoppingListService.addItem("sugar", new BigDecimal("1"), "kg"));
            adds.add(() -> shoppingListService.addItem("Sugar", new BigDecimal("100"), "G"));
        }
        runConcurrently(adds);

        // 500 g + 10 x 1000 g + 10 x 100 g
        assertEquals(0, new BigDecimal("11500").compareTo(quantityOf("sugar")));
        assertEquals(1, rows("sugar"));
    }

    @Test
    void concurrentAddsOfANewItem_neverCreateDuplicates_andNeverSilentlyDropAnAdd() throws Exception {
        List<Callable<ShoppingListItem>> adds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            adds.add(() -> shoppingListService.addItem("saffron", BigDecimal.ONE, "g"));
        }
        List<Object> outcomes = runConcurrently(adds);

        long succeeded = outcomes.stream().filter(o -> o instanceof ShoppingListItem).count();
        outcomes.stream().filter(o -> !(o instanceof ShoppingListItem))
                .forEach(failure -> assertInstanceOf(DataIntegrityViolationException.class, failure,
                        "the only acceptable failure is the unique-name conflict (reported as 409)"));
        assertEquals(1, rows("saffron"));
        // Whatever succeeded is in the total; whatever failed is not. Nothing is half-applied.
        assertEquals(0, BigDecimal.valueOf(succeeded).compareTo(quantityOf("saffron")));
    }

    @Test
    void namesThatDifferOnlyByCaseOrWhitespace_mergeIntoOneRow() {
        shoppingListService.addItem("Eggs", new BigDecimal("2"), "pcs");
        shoppingListService.addItem("eggs", new BigDecimal("3"), "pcs");
        shoppingListService.addItem("  EGGS ", new BigDecimal("1"), "pcs");

        assertEquals(1, rows("eggs"));
        assertEquals(0, new BigDecimal("6").compareTo(quantityOf("eggs")));
    }

    @Test
    void incompatibleUnitsGoToTheirOwnRow_withTheRightTotals() {
        shoppingListService.addItem("onion", BigDecimal.ONE, "pcs");
        shoppingListService.addItem("onion", new BigDecimal("200"), "g");
        shoppingListService.addItem("onion", BigDecimal.ONE, "kg");

        assertEquals(0, BigDecimal.ONE.compareTo(quantityOf("onion")));
        assertEquals(0, new BigDecimal("1200").compareTo(quantityOf("onion (weight)")));
    }

    @Test
    void addingAndDeletingRecipes_keepsTheShoppingListConsistent() {
        Long pancakes = recipeService.addRecipe("Pancakes", RecipeSource.MANUAL, null, List.of(
                new IngredientRequest("flour", new BigDecimal("200"), "g"),
                new IngredientRequest("eggs", new BigDecimal("2"), "pcs"))).getId();
        Long bread = recipeService.addRecipe("Bread", RecipeSource.MANUAL, null, List.of(
                new IngredientRequest("flour", new BigDecimal("1"), "kg"),
                new IngredientRequest("yeast", new BigDecimal("7"), "g"))).getId();
        assertEquals(0, new BigDecimal("1200").compareTo(quantityOf("flour")));

        recipeService.deleteRecipe(bread, true);

        assertEquals(0, new BigDecimal("200").compareTo(quantityOf("flour")));
        assertEquals(0, rows("yeast"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM recipes", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ingredients", Integer.class));

        recipeService.deleteRecipe(pancakes, false);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredients", Integer.class));
        assertEquals(0, new BigDecimal("200").compareTo(quantityOf("flour")));
    }

    @Test
    void aFailedRecipeSave_leavesNothingBehind() {
        // The second ingredient has no name, which the database refuses, so the whole recipe must roll back, and
        // with it the shopping list changes made for the first ingredient.
        List<IngredientRequest> ingredients = List.of(
                new IngredientRequest("flour", new BigDecimal("200"), "g"),
                new IngredientRequest(null, BigDecimal.ONE, "g"));

        try {
            recipeService.addRecipe("Broken", RecipeSource.MANUAL, null, ingredients);
        } catch (RuntimeException expected) {
            // the failure is the point
        }

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM recipes", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredients", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM shopping_list_items", Integer.class));
    }
}
