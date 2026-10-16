package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.repository.RecipeRepository;
import com.malyszczuk.ingredients_retriever.repository.ShoppingListItemRepository;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import com.malyszczuk.ingredients_retriever.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real schema on a real PostgreSQL: what Flyway builds, and what the database itself refuses to store. */
@RequiresDocker
class DatabaseSchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ShoppingListItemRepository shoppingListItemRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long userId;

    @BeforeEach
    void emptyTables() {
        jdbc.update("DELETE FROM ingredients");
        jdbc.update("DELETE FROM recipes");
        jdbc.update("DELETE FROM shopping_list_items");
        jdbc.update("DELETE FROM users");
        userId = TestUsers.create(jdbc, "schema@example.com").getId();
    }

    @Test
    void flywayAppliedTheInitialMigration_andHibernateAcceptedItAsMatchingTheEntities() {
        // If the entities and the migration disagreed, ddl-auto=validate would have stopped the application from starting.
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertEquals("1", history.getFirst().get("version"));
        assertTrue(history.stream().allMatch(row -> Boolean.TRUE.equals(row.get("success"))));
    }

    @Test
    void shoppingListItemNames_areUniqueIgnoringCase_enforcedByTheDatabase() {
        shoppingListItemRepository.saveAndFlush(ShoppingListItem.builder().userId(userId).name("Eggs").build());

        assertThrows(DataIntegrityViolationException.class,
                () -> shoppingListItemRepository.saveAndFlush(ShoppingListItem.builder().userId(userId).name("eggs").build()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO shopping_list_items (user_id, name, purchased) VALUES (?, 'EGGS', false)", userId));
    }

    @Test
    void caseInsensitiveLookup_findsTheItemWhateverTheCase() {
        shoppingListItemRepository.saveAndFlush(ShoppingListItem.builder().userId(userId).name("Eggs").build());

        List<String> found = transactionTemplate.execute(status -> List.of(
                shoppingListItemRepository.findByUserIdAndNameIgnoreCase(userId, "eggs").orElseThrow().getName(),
                shoppingListItemRepository.findByUserIdAndNameIgnoreCase(userId, "EGGS").orElseThrow().getName()));

        assertEquals(List.of("Eggs", "Eggs"), found);
    }

    @Test
    void quantities_keepThreeDecimals() {
        // H2's default silently rounded to two (8.763 became 8.76); the unit converter works to three.
        Long id = shoppingListItemRepository.saveAndFlush(
                ShoppingListItem.builder().userId(userId).name("milk").quantity(new BigDecimal("8.763")).unit("tbsp").build()).getId();

        BigDecimal stored = jdbc.queryForObject("SELECT quantity FROM shopping_list_items WHERE id = ?", BigDecimal.class, id);

        assertEquals(0, new BigDecimal("8.763").compareTo(stored));
    }

    @Test
    void recipeWithIngredients_roundTrips_andLongSourceReferencesFit() {
        String longUrl = "https://example.com/recipes/" + "a".repeat(1500);
        Recipe recipe = Recipe.builder().userId(userId).title("Pancakes").sourceType(RecipeSource.URL).sourceReference(longUrl).build();
        recipe.addIngredient(Ingredient.builder().name("flour").quantity(new BigDecimal("1.500")).unit("cups").build());
        recipe.addIngredient(Ingredient.builder().name("salt").build());
        Long id = recipeRepository.saveAndFlush(recipe).getId();

        Recipe loaded = transactionTemplate.execute(status -> {
            Recipe found = recipeRepository.findByIdAndUserId(id, userId).orElseThrow();
            found.getIngredients().size();
            return found;
        });

        assertEquals(longUrl, loaded.getSourceReference());
        assertEquals(RecipeSource.URL, loaded.getSourceType());
        assertEquals(List.of("flour", "salt"), loaded.getIngredients().stream().map(Ingredient::getName).sorted().toList());
        assertTrue(loaded.getCreatedAt() != null);
    }

    @Test
    void sourceType_isRestrictedToKnownValues_bytheDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO recipes (user_id, title, source_type, created_at) VALUES (?, 'x', 'BOGUS', now())", userId));
    }

    @Test
    void ingredientsCannotExistWithoutARecipe() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO ingredients (name, recipe_id) VALUES ('orphan', 999999)"));
    }

    @Test
    void deletingARecipe_removesItsIngredients_evenWhenTheApplicationIsNotInvolved() {
        Long recipeId = jdbc.queryForObject(
                "INSERT INTO recipes (user_id, title, source_type, created_at) VALUES (?, 'x', 'MANUAL', now()) RETURNING id", Long.class, userId);
        jdbc.update("INSERT INTO ingredients (name, recipe_id) VALUES ('a', ?), ('b', ?)", recipeId, recipeId);

        jdbc.update("DELETE FROM recipes WHERE id = ?", recipeId);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ingredients", Integer.class));
    }
}
