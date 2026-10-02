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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddRecipeToolTest {

    @Mock
    private RecipeService recipeService;

    private AddRecipeTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddRecipeTool(recipeService);
    }

    private Recipe recipe(RecipeSource source) {
        Recipe recipe = Recipe.builder().id(5L).title("Pancakes").sourceType(source).build();
        recipe.addIngredient(Ingredient.builder().name("flour").quantity(new BigDecimal("200.00")).unit("g").build());
        recipe.addIngredient(Ingredient.builder().name("eggs").quantity(BigDecimal.valueOf(2)).build());
        recipe.addIngredient(Ingredient.builder().name("salt").build());
        return recipe;
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_addsByDishName_andSaysTheIngredientsAreSuggestions() {
        when(recipeService.addRecipeFromText("pancakes")).thenReturn(recipe(RecipeSource.TEXT));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("recipe", "  pancakes "));

        assertEquals(5L, result.get("recipeId"));
        assertEquals("Pancakes", result.get("title"));
        assertEquals(List.of("200 g flour", "2 eggs", "salt"), result.get("ingredients"));
        assertEquals(true, result.get("addedToShoppingList"));
        assertTrue(result.get("note").toString().contains("suggested by AI"));
        verify(recipeService, never()).addRecipeFromUrl(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_addsByUrl_withoutTheSuggestionNote() {
        when(recipeService.addRecipeFromUrl("https://example.com/pancakes")).thenReturn(recipe(RecipeSource.URL));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("recipe", "https://example.com/pancakes"));

        assertEquals("Pancakes", result.get("title"));
        assertFalse(result.containsKey("note"));
        verify(recipeService, never()).addRecipeFromText(any());
    }

    @Test
    void execute_recognisesUrlsCaseInsensitively() {
        when(recipeService.addRecipeFromUrl("HTTP://EXAMPLE.COM/R")).thenReturn(recipe(RecipeSource.URL));

        tool.execute(Map.of("recipe", "HTTP://EXAMPLE.COM/R"));

        verify(recipeService).addRecipeFromUrl("HTTP://EXAMPLE.COM/R");
    }

    @Test
    void execute_treatsNonHttpSchemesAsDishNames_notAsUrls() {
        when(recipeService.addRecipeFromText("file:///etc/passwd")).thenReturn(recipe(RecipeSource.TEXT));

        tool.execute(Map.of("recipe", "file:///etc/passwd"));

        verify(recipeService, never()).addRecipeFromUrl(any());
    }

    @Test
    void execute_rejectsABlankOrMissingRecipe() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("recipe", "   ")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of()));
        verify(recipeService, never()).addRecipeFromText(any());
    }
}
