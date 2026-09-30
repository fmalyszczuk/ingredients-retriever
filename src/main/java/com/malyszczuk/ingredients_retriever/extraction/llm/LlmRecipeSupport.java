package com.malyszczuk.ingredients_retriever.extraction.llm;

import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.extraction.text.ExtractedRecipe;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The JSON schema every LLM-backed recipe extractor asks for, and the parsing/validation of the reply. */
public final class LlmRecipeSupport {

    // Enum instead of a free string|null union: with the union, the model skipped units entirely.
    private static final List<String> ALLOWED_UNITS = List.of("g", "kg", "ml", "l", "tsp", "tbsp", "cup", "pcs", "none");

    public static final Map<String, Object> SCHEMA = buildSchema();

    private LlmRecipeSupport() {
    }

    // Property order matters to constrained decoding: is_dish must be decided before the ingredients are written.
    private static Map<String, Object> buildSchema() {
        Map<String, Object> ingredientProperties = new LinkedHashMap<>();
        ingredientProperties.put("name", Map.of("type", "string"));
        ingredientProperties.put("quantity", Map.of("type", "number"));
        ingredientProperties.put("unit", Map.of("type", "string", "enum", ALLOWED_UNITS));

        Map<String, Object> ingredientSchema = new LinkedHashMap<>();
        ingredientSchema.put("type", "object");
        ingredientSchema.put("properties", ingredientProperties);
        ingredientSchema.put("required", List.of("name", "quantity", "unit"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("is_dish", Map.of("type", "boolean"));
        properties.put("title", Map.of("type", "string"));
        properties.put("ingredients", Map.of("type", "array", "items", ingredientSchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("is_dish", "title", "ingredients"));
        return schema;
    }

    /**
     * @param subject      how to refer to the input in error messages, e.g. {@code 'pancakes'} or {@code file 'a.pdf'}
     * @param fallbackTitle used when the model returns a blank title
     */
    public static ExtractedRecipe parse(ObjectMapper objectMapper, String json, String subject, String fallbackTitle) {
        if (json == null || json.isBlank()) {
            throw new RecipeExtractionException("The model returned an empty response for " + subject);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new RecipeExtractionException("The model returned an invalid recipe for " + subject, e);
        }

        if (!root.path("is_dish").asBoolean(true)) {
            throw new RecipeExtractionException(subject + " does not look like a recipe");
        }

        List<IngredientRequest> ingredients = parseIngredients(root.path("ingredients"));
        if (ingredients.isEmpty()) {
            throw new RecipeExtractionException("Could not find any ingredients in " + subject);
        }

        String title = root.path("title").asString("").trim();
        return new ExtractedRecipe(title.isEmpty() ? fallbackTitle : title, ingredients);
    }

    private static List<IngredientRequest> parseIngredients(JsonNode ingredientsNode) {
        List<IngredientRequest> ingredients = new ArrayList<>();
        for (JsonNode node : ingredientsNode) {
            String name = node.path("name").asString("").trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            ingredients.add(new IngredientRequest(name, parseQuantity(node.path("quantity")), parseUnit(node.path("unit"))));
        }
        return ingredients;
    }

    private static BigDecimal parseQuantity(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        BigDecimal quantity = node.decimalValue();
        return quantity.signum() > 0 ? quantity : null;
    }

    private static String parseUnit(JsonNode node) {
        String unit = node.asString("").trim().toLowerCase(Locale.ROOT);
        return unit.isEmpty() || unit.equals("none") ? null : unit;
    }
}
