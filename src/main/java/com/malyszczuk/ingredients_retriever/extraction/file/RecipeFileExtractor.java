package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.agent.OllamaChatClient;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.extraction.llm.LlmRecipeSupport;
import com.malyszczuk.ingredients_retriever.extraction.text.ExtractedRecipe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Extracts a recipe from an uploaded file. PDF, DOCX and text files are read to text and sent to the
 * text model; images are sent directly to the vision model.
 */
@Component
@RequiredArgsConstructor
public class RecipeFileExtractor {

    // Ollama's default context window is small (4096 tokens on an 8 GB GPU); longer text would be silently cut off.
    static final int MAX_TEXT_CHARS = 8000;

    private static final String INSTRUCTIONS = """
            Extract the recipe as JSON.
            - "is_dish": false if there is no recipe (then leave "ingredients" empty), otherwise true.
            - "title": the recipe title as written.
            - "ingredients": ONLY the ingredients the recipe lists, with their amounts as written. Do not add ingredients that are not listed.
              - "name": lowercase, singular where natural, no preparation notes (write "onion", not "chopped onion").
              - "quantity": always a positive number (use decimals for fractions). If no amount is given (e.g. "salt to taste"), use a small amount such as 1 tsp.
              - "unit": one of g, kg, ml, l, tsp, tbsp, cup, pcs, or "none". If the written unit is not in this list, convert to the closest one (1 lb is about 450 g, 1 oz is about 30 g).
            """;

    private static final String DOCUMENT_PROMPT = """
            You are a cooking assistant. The text inside the <document> tags contains a recipe, possibly with other text around it.
            %s
            <document>%s</document>
            """;

    private static final String IMAGE_PROMPT = """
            You are a cooking assistant. The attached image shows a recipe (a screenshot or a photo of a page).
            %s
            """;

    private final DocumentTextReader documentTextReader;
    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;

    public ExtractedRecipe extract(String filename, byte[] content) {
        FileKind kind = FileKind.detect(filename);
        String subject = "file '" + filename + "'";
        String fallbackTitle = stripExtension(filename);

        String json;
        if (kind == FileKind.IMAGE) {
            json = ollamaChatClient.chatStructuredWithImage(
                    IMAGE_PROMPT.formatted(INSTRUCTIONS), content, LlmRecipeSupport.SCHEMA);
        } else {
            String text = documentTextReader.read(kind, filename, content).trim();
            if (text.isEmpty()) {
                throw new RecipeExtractionException("No text found in " + subject
                        + (kind == FileKind.PDF ? " (is it a scanned PDF? try uploading it as an image)" : ""));
            }
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS);
            }
            json = ollamaChatClient.chatStructured(
                    DOCUMENT_PROMPT.formatted(INSTRUCTIONS, text), LlmRecipeSupport.SCHEMA);
        }

        return LlmRecipeSupport.parse(objectMapper, json, subject, fallbackTitle);
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
