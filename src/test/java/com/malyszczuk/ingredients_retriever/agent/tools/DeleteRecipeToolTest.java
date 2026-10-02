package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteRecipeToolTest {

    @Mock
    private RecipeService recipeService;

    private DeleteRecipeTool tool;

    @BeforeEach
    void setUp() {
        tool = new DeleteRecipeTool(recipeService);
    }

    private Recipe recipe(long id, String title) {
        return Recipe.builder().id(id).title(title).sourceType(RecipeSource.MANUAL).build();
    }

    private void recipeExists(long id, String title) {
        when(recipeService.getRecipe(id)).thenReturn(recipe(id, title));
    }

    // ---- by id

    @Test
    @SuppressWarnings("unchecked")
    void execute_deletesTheRecipe_andLeavesTheShoppingListAloneByDefault() {
        recipeExists(3, "Pancakes");

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("id", 3));

        verify(recipeService).deleteRecipe(3L, false);
        assertEquals(3L, result.get("deletedRecipeId"));
        assertEquals("Pancakes", result.get("title"));
        assertEquals(false, result.get("removedIngredientsFromShoppingList"));
    }

    @Test
    void execute_canAlsoRemoveTheIngredientsFromTheShoppingList() {
        recipeExists(3, "Pancakes");

        tool.execute(Map.of("id", 3, "remove_from_shopping_list", true));

        verify(recipeService).deleteRecipe(3L, true);
    }

    @Test
    void execute_acceptsTheIdAsNumberOrText() {
        recipeExists(3, "Pancakes");

        tool.execute(Map.of("id", "3"));
        tool.execute(Map.of("id", 3.0));

        verify(recipeService, times(2)).deleteRecipe(3L, false);
    }

    @Test
    void execute_rejectsAnIdThatIsNotAWholeNumber() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("id", "pancakes")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("id", 3.5)));
        verify(recipeService, never()).deleteRecipe(anyLong(), anyBoolean());
    }

    @Test
    void execute_deletesNothing_whenTheRecipeDoesNotExist() {
        when(recipeService.getRecipe(9L)).thenThrow(new NoSuchElementException("No recipe with id 9"));

        assertThrows(NoSuchElementException.class, () -> tool.execute(Map.of("id", 9)));

        verify(recipeService, never()).deleteRecipe(anyLong(), anyBoolean());
    }

    // ---- by title

    @Test
    @SuppressWarnings("unchecked")
    void execute_findsTheRecipeByAPartOfItsTitle_ignoringCase() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Spaghetti Carbonara"), recipe(2, "Fluffy Pancakes")));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("title", "CARBONARA"));

        verify(recipeService).deleteRecipe(1L, false);
        assertEquals("Spaghetti Carbonara", result.get("title"));
    }

    @Test
    void execute_prefersAnExactTitleOverPartialMatches() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Pancakes"), recipe(2, "Pancakes with berries")));

        tool.execute(Map.of("title", "pancakes"));

        verify(recipeService).deleteRecipe(1L, false);
    }

    @Test
    void execute_refusesToGuess_whenSeveralRecipesMatch() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Pancakes"), recipe(2, "Banana pancakes")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("title", "cake")));

        assertTrue(error.getMessage().contains("[1] Pancakes") && error.getMessage().contains("[2] Banana pancakes"));
        verify(recipeService, never()).deleteRecipe(anyLong(), anyBoolean());
    }

    @Test
    void execute_failsClearly_whenNoTitleMatches() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Pancakes")));

        assertThrows(NoSuchElementException.class, () -> tool.execute(Map.of("title", "lasagne")));
        verify(recipeService, never()).deleteRecipe(anyLong(), anyBoolean());
    }

    @Test
    void execute_needsAnIdOrATitle() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("title", "  ")));
    }

    // ---- confirmation

    @Test
    void confirmationTarget_isTheResolvedRecipeId_howeverItWasGiven() {
        recipeExists(3, "Pancakes");
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(3, "Pancakes")));

        assertEquals("3", tool.confirmationTarget(Map.of("id", 3)));
        assertEquals("3", tool.confirmationTarget(Map.of("id", "3")));
        assertEquals("3", tool.confirmationTarget(Map.of("title", "pancakes")));
    }

    @Test
    void confirmationTarget_isEmpty_whenTheRecipeCannotBeResolved() {
        when(recipeService.listRecipes()).thenReturn(List.of());

        assertEquals("", tool.confirmationTarget(Map.of("title", "lasagne")));
        assertEquals("", tool.confirmationTarget(Map.of("id", "abc")));
        assertEquals("", tool.confirmationTarget(Map.of()));
    }

    @Test
    void confirmationDescription_namesTheExactRecipe() {
        when(recipeService.listRecipes()).thenReturn(List.of(recipe(1, "Spaghetti Carbonara")));

        assertEquals("Delete the saved recipe 'Spaghetti Carbonara' (id 1)",
                tool.confirmationDescription(Map.of("title", "carbonara")));
        assertEquals("", tool.confirmationDescription(Map.of("title", "lasagne")));
    }

    @Test
    void deletingIsConfirmedAndChangesTheList() {
        assertTrue(tool.requiresConfirmation());
        assertTrue(tool.changesShoppingList());
    }
}
