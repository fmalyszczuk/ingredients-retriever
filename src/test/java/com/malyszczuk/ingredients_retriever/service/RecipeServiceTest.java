package com.malyszczuk.ingredients_retriever.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;
import java.util.NoSuchElementException;
import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.IngredientLineParser;
import com.malyszczuk.ingredients_retriever.extraction.ParsedIngredientLine;
import com.malyszczuk.ingredients_retriever.extraction.file.RecipeFileExtractor;
import com.malyszczuk.ingredients_retriever.extraction.text.ExtractedRecipe;
import com.malyszczuk.ingredients_retriever.extraction.text.RecipeTextExtractor;
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

    @Mock
    private RecipeTextExtractor recipeTextExtractor;

    @Mock
    private RecipeFileExtractor recipeFileExtractor;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(recipeRepository, shoppingListService, recipeUrlScraper, ingredientLineParser,
                recipeTextExtractor, recipeFileExtractor);
    }

    @Test
    void addRecipeFromFile_persistsExtractedRecipeAsFileSourced_andMergesIntoShoppingList() {
        byte[] content = {1, 2, 3};
        when(recipeFileExtractor.extract("pancakes.pdf", content)).thenReturn(new ExtractedRecipe("Pancakes",
                List.of(new IngredientRequest("flour", BigDecimal.valueOf(200), "g"))));
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Recipe result = recipeService.addRecipeFromFile("pancakes.pdf", content);

        assertEquals("Pancakes", result.getTitle());
        assertEquals(RecipeSource.FILE, result.getSourceType());
        assertEquals("pancakes.pdf", result.getSourceReference());
        assertEquals("flour", result.getIngredients().getFirst().getName());
        verify(shoppingListService).addIngredients(any());
    }

    @Test
    void addRecipeFromText_persistsExtractedRecipeAsTextSourced_andMergesIntoShoppingList() {
        when(recipeTextExtractor.extract("pancakes")).thenReturn(new ExtractedRecipe("Pancakes",
                List.of(new IngredientRequest("flour", BigDecimal.valueOf(200), "g"))));
        when(recipeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Recipe result = recipeService.addRecipeFromText("  pancakes ");

        assertEquals("Pancakes", result.getTitle());
        assertEquals(RecipeSource.TEXT, result.getSourceType());
        assertEquals("pancakes", result.getSourceReference());
        assertEquals("flour", result.getIngredients().getFirst().getName());
        verify(shoppingListService).addIngredients(any());
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

    @Test
    void getRecipe_returnsRecipe_whenPresent() {
        Recipe recipe = Recipe.builder().id(1L).title("Pancakes").sourceType(RecipeSource.MANUAL).build();
        when(recipeRepository.findById(1L)).thenReturn(Optional.of(recipe));

        assertSame(recipe, recipeService.getRecipe(1L));
    }

    @Test
    void getRecipe_throws_whenMissing() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> recipeService.getRecipe(9L));
    }

    @Test
    void deleteRecipe_deletesOnly_byDefault() {
        Recipe recipe = Recipe.builder().id(1L).title("Pancakes").sourceType(RecipeSource.MANUAL).build();
        when(recipeRepository.findById(1L)).thenReturn(Optional.of(recipe));

        recipeService.deleteRecipe(1L, false);

        verify(recipeRepository).delete(recipe);
        verifyNoInteractions(shoppingListService);
    }

    @Test
    void deleteRecipe_alsoRemovesIngredientsFromShoppingList_whenRequested() {
        Recipe recipe = Recipe.builder().id(1L).title("Pancakes").sourceType(RecipeSource.MANUAL).build();
        recipe.addIngredient(Ingredient.builder().name("flour").quantity(BigDecimal.valueOf(200)).unit("g").build());
        when(recipeRepository.findById(1L)).thenReturn(Optional.of(recipe));

        recipeService.deleteRecipe(1L, true);

        verify(shoppingListService).removeIngredients(recipe.getIngredients());
        verify(recipeRepository).delete(recipe);
    }

    @Test
    void deleteRecipe_throwsAndDeletesNothing_whenMissing() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> recipeService.deleteRecipe(9L, true));
        verify(recipeRepository, never()).delete(any());
        verifyNoInteractions(shoppingListService);
    }
}
