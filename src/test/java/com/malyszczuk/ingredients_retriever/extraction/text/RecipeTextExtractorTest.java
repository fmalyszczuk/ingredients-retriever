package com.malyszczuk.ingredients_retriever.extraction.text;

import com.malyszczuk.ingredients_retriever.agent.OllamaChatClient;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeTextExtractorTest {

    @Mock
    private OllamaChatClient ollamaChatClient;

    private RecipeTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RecipeTextExtractor(ollamaChatClient, new ObjectMapper());
    }

    @Test
    void extract_parsesTitleAndIngredients_andEmbedsDishInPrompt() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"title": "Spaghetti Carbonara", "ingredients": [
                  {"name": "Spaghetti", "quantity": 400, "unit": "g"},
                  {"name": " Eggs ", "quantity": 4, "unit": "PCS"},
                  {"name": "black pepper", "quantity": null, "unit": "none"}
                ]}""");

        ExtractedRecipe result = extractor.extract("  spaghetti carbonara ");

        assertEquals("Spaghetti Carbonara", result.title());
        assertEquals(3, result.ingredients().size());
        assertEquals("spaghetti", result.ingredients().get(0).name());
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.ingredients().get(0).quantity()));
        assertEquals("eggs", result.ingredients().get(1).name());
        assertEquals("pcs", result.ingredients().get(1).unit());
        assertNull(result.ingredients().get(2).quantity());
        assertNull(result.ingredients().get(2).unit());
        verify(ollamaChatClient).chatStructured(contains("<dish>spaghetti carbonara</dish>"), any());
    }

    @Test
    void extract_dropsNonPositiveQuantitiesAndBlankNames() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"title": "Soup", "ingredients": [
                  {"name": "salt", "quantity": 0, "unit": ""},
                  {"name": "  ", "quantity": 1, "unit": "g"}
                ]}""");

        ExtractedRecipe result = extractor.extract("soup");

        assertEquals(1, result.ingredients().size());
        IngredientRequest salt = result.ingredients().getFirst();
        assertEquals("salt", salt.name());
        assertNull(salt.quantity());
        assertNull(salt.unit());
    }

    @Test
    void extract_fallsBackToInputText_whenTitleIsBlank() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"title": " ", "ingredients": [{"name": "rice", "quantity": 1, "unit": "cup"}]}""");

        assertEquals("fried rice", extractor.extract("fried rice").title());
    }

    @Test
    void extract_throws_whenModelSaysInputIsNotADish() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"is_dish": false, "title": "", "ingredients": [{"name": "salt", "quantity": 1, "unit": "tsp"}]}""");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("asdfghjkl"));
    }

    @Test
    void extract_throws_whenResponseIsMalformedJson() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("not json at all");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("soup"));
    }

    @Test
    void extract_throws_whenResponseIsEmpty() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn(" ");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("soup"));
    }

    @Test
    void extract_throws_whenNoIngredientsReturned() {
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"title": "Soup", "ingredients": []}""");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("soup"));
    }
}
