package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.IngredientLineParser;
import com.malyszczuk.ingredients_retriever.extraction.ParsedIngredientLine;
import com.malyszczuk.ingredients_retriever.extraction.url.RawRecipe;
import com.malyszczuk.ingredients_retriever.extraction.url.RecipeUrlScraper;
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

    @Mock
    private RecipeUrlScraper recipeUrlScraper;

    @Mock
    private IngredientLineParser ingredientLineParser;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(recipeRepository, shoppingListService, recipeUrlScraper, ingredientLineParser);
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

    @Test
    void addRecipeFromUrl_scrapesAndParsesLines_thenPersistsAsUrlSourcedRecipe() {
        String url = "https://example.com/pancakes";
        RawRecipe rawRecipe = new RawRecipe("Pancakes", List.of("2 eggs", "200 g flour"));
        when(recipeUrlScraper.scrape(url)).thenReturn(rawRecipe);
        when(ingredientLineParser.parse("2 eggs")).thenReturn(new ParsedIngredientLine("eggs", BigDecimal.valueOf(2), null));
        when(ingredientLineParser.parse("200 g flour")).thenReturn(new ParsedIngredientLine("flour", BigDecimal.valueOf(200), "g"));
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Recipe result = recipeService.addRecipeFromUrl(url);

        assertEquals("Pancakes", result.getTitle());
        assertEquals(RecipeSource.URL, result.getSourceType());
        assertEquals(url, result.getSourceReference());
        assertEquals(2, result.getIngredients().size());
        assertEquals("eggs", result.getIngredients().getFirst().getName());
        assertEquals("flour", result.getIngredients().get(1).getName());
        assertEquals("g", result.getIngredients().get(1).getUnit());
    }
}
