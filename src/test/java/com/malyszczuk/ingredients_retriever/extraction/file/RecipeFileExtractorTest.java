package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.agent.OllamaChatClient;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.extraction.text.ExtractedRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeFileExtractorTest {

    private static final String RECIPE_JSON = """
            {"is_dish": true, "title": "Pancakes", "ingredients": [{"name": "Flour", "quantity": 250, "unit": "g"}]}""";

    @Mock
    private DocumentTextReader documentTextReader;

    @Mock
    private OllamaChatClient ollamaChatClient;

    private RecipeFileExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RecipeFileExtractor(documentTextReader, ollamaChatClient, new ObjectMapper());
    }

    @Test
    void extract_sendsDocumentTextToTheTextModel() {
        byte[] content = {1, 2, 3};
        when(documentTextReader.read(FileKind.PDF, "pancakes.pdf", content)).thenReturn("  250 g flour  ");
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn(RECIPE_JSON);

        ExtractedRecipe result = extractor.extract("pancakes.pdf", content);

        assertEquals("Pancakes", result.title());
        assertEquals("flour", result.ingredients().getFirst().name());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ollamaChatClient).chatStructured(prompt.capture(), any());
        assertTrue(prompt.getValue().contains("<document>250 g flour</document>"));
        verify(ollamaChatClient, never()).chatStructuredWithImage(any(), any(), any());
    }

    @Test
    void extract_sendsImagesToTheVisionModel_withoutReadingText() {
        byte[] image = {9, 8, 7};
        when(ollamaChatClient.chatStructuredWithImage(any(), eq(image), any())).thenReturn(RECIPE_JSON);

        ExtractedRecipe result = extractor.extract("screenshot.png", image);

        assertEquals("Pancakes", result.title());
        verifyNoInteractions(documentTextReader);
        verify(ollamaChatClient, never()).chatStructured(any(), any());
    }

    @Test
    void extract_truncatesVeryLongDocuments() {
        when(documentTextReader.read(any(), any(), any())).thenReturn("a".repeat(RecipeFileExtractor.MAX_TEXT_CHARS + 500));
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn(RECIPE_JSON);

        extractor.extract("big.txt", new byte[]{1});

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ollamaChatClient).chatStructured(prompt.capture(), any());
        assertTrue(prompt.getValue().contains("a".repeat(RecipeFileExtractor.MAX_TEXT_CHARS)));
        assertFalse(prompt.getValue().contains("a".repeat(RecipeFileExtractor.MAX_TEXT_CHARS + 1)));
    }

    @Test
    void extract_usesFilenameAsTitle_whenModelReturnsBlankTitle() {
        when(documentTextReader.read(any(), any(), any())).thenReturn("x");
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"is_dish": true, "title": "", "ingredients": [{"name": "rice", "quantity": 1, "unit": "cup"}]}""");

        assertEquals("grandmas soup", extractor.extract("grandmas soup.txt", new byte[]{1}).title());
    }

    @Test
    void extract_throws_whenFileHasNoText() {
        when(documentTextReader.read(any(), any(), any())).thenReturn("  \n ");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("scan.pdf", new byte[]{1}));
        verifyNoInteractions(ollamaChatClient);
    }

    @Test
    void extract_throws_whenModelFindsNoRecipe() {
        when(documentTextReader.read(any(), any(), any())).thenReturn("meeting notes");
        when(ollamaChatClient.chatStructured(any(), any())).thenReturn("""
                {"is_dish": false, "title": "", "ingredients": []}""");

        assertThrows(RecipeExtractionException.class, () -> extractor.extract("notes.txt", new byte[]{1}));
    }

    @Test
    void extract_throws_forUnsupportedFileTypes() {
        assertThrows(UnsupportedFileTypeException.class, () -> extractor.extract("old.doc", new byte[]{1}));
        verifyNoInteractions(documentTextReader, ollamaChatClient);
    }
}
