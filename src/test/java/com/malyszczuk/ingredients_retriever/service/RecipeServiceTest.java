package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private ShoppingListService shoppingListService;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(recipeRepository, shoppingListService);
    }

    @Test
    void addRecipe_persistsRecipeWithIngredients_andMergesIntoShoppingList() {
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<IngredientRequest> requests = List.of(
                new IngredientRequest("eggs", BigDecimal.valueOf(2), "pcs"),
                new IngredientRequest("flour", BigDecimal.valueOf(200), "g"));

        Recipe result = recipeService.addRecipe("Pancakes", RecipeSource.MANUAL, null, requests);

        assertEquals("Pancakes", result.getTitle());
        assertEquals(2, result.getIngredients().size());
        assertEquals("eggs", result.getIngredients().getFirst().getName());
        assertSame(result, result.getIngredients().getFirst().getRecipe());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ingredient>> captor = ArgumentCaptor.forClass(List.class);
        verify(shoppingListService).addIngredients(captor.capture());
        assertEquals(2, captor.getValue().size());
    }
}
