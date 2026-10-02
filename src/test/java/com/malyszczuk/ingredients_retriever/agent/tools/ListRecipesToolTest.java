package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRecipesToolTest {

    @Mock
    private RecipeService recipeService;

    private ListRecipesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListRecipesTool(recipeService);
    }

    private Recipe recipe(long id, String title) {
        Recipe recipe = Recipe.builder().id(id).title(title).sourceType(RecipeSource.MANUAL).build();
        recipe.addIngredient(Ingredient.builder().name("flour").build());
        return recipe;
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_listsRecipesNewestFirst_withIdTitleAndIngredients() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Old"), recipe(2, "New")));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of());

        List<Map<String, Object>> recipes = (List<Map<String, Object>>) result.get("recipes");
        assertEquals(2, result.get("totalRecipes"));
        assertEquals(List.of(2L, 1L), recipes.stream().map(r -> r.get("id")).toList());
        assertEquals("New", recipes.getFirst().get("title"));
        assertEquals(List.of("flour"), recipes.getFirst().get("ingredients"));
        assertFalse(result.containsKey("note"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_capsTheListAndSaysSo() {
        List<Recipe> many = new ArrayList<>();
        for (long id = 1; id <= ListRecipesTool.MAX_RECIPES + 3; id++) {
            many.add(recipe(id, "Recipe " + id));
        }
        when(recipeService.listRecipes()).thenReturn(many);

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of());

        List<Map<String, Object>> recipes = (List<Map<String, Object>>) result.get("recipes");
        assertEquals(ListRecipesTool.MAX_RECIPES, recipes.size());
        assertEquals(ListRecipesTool.MAX_RECIPES + 3, result.get("totalRecipes"));
        assertEquals((long) ListRecipesTool.MAX_RECIPES + 3, recipes.getFirst().get("id"));
        assertTrue(result.get("note").toString().contains("newest"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_handlesNoRecipes() {
        when(recipeService.listRecipes()).thenReturn(List.of());

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of());

        assertEquals(0, result.get("totalRecipes"));
        assertTrue(((List<?>) result.get("recipes")).isEmpty());
    }
}
