package com.malyszczuk.ingredients_retriever.extraction.text;

import com.malyszczuk.ingredients_retriever.agent.OllamaChatClient;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a free-text dish name (e.g. "spaghetti carbonara") into a recipe by asking the local LLM
 * for a typical ingredient list. The ingredients come from the model's memory, not a real recipe page.
 */
@Component
@RequiredArgsConstructor
public class RecipeTextExtractor {

    private static final String PROMPT_TEMPLATE = """
            You are a cooking assistant. The user wants to cook the dish inside the <dish> tags below.
            Reply with a typical recipe for about 4 servings as JSON.
            - "is_dish": false if the text is not the name of a food or dish (then leave "ingredients" empty), otherwise true.
            - "title": the dish name, in title case.
            - "ingredients": every ingredient needed to shop for.
              - "name": lowercase, singular where natural, no preparation notes (write "onion", not "chopped onion").
              - "quantity": always a positive number. For seasonings "to taste", use a small amount such as 1 tsp.
              - "unit": one of g, kg, ml, l, tsp, tbsp, cup, pcs, or "none". Use g for solids (400 g spaghetti), ml for liquids, pcs for countable items (4 pcs eggs).
            <dish>%s</dish>
            """;

    // Enum instead of a free string|null union: with the union, the model skipped units entirely.
    private static final List<String> ALLOWED_UNITS = List.of("g", "kg", "ml", "l", "tsp", "tbsp", "cup", "pcs", "none");

    private static final Map<String, Object> RESPONSE_SCHEMA = responseSchema();

    // Property order matters to constrained decoding: is_dish must be decided before the ingredients are written.
    private static Map<String, Object> responseSchema() {
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

    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;

    public ExtractedRecipe extract(String text) {
        String json = ollamaChatClient.chatStructured(PROMPT_TEMPLATE.formatted(text.trim()), RESPONSE_SCHEMA);
        if (json == null || json.isBlank()) {
            throw new RecipeExtractionException("The model returned an empty response for '" + text + "'");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new RecipeExtractionException("The model returned an invalid recipe for '" + text + "'", e);
        }

        if (!root.path("is_dish").asBoolean(true)) {
            throw new RecipeExtractionException("'" + text + "' does not look like a dish name");
        }

        List<IngredientRequest> ingredients = parseIngredients(root.path("ingredients"));
        if (ingredients.isEmpty()) {
            throw new RecipeExtractionException("Could not find any ingredients for '" + text + "'");
        }

        String title = root.path("title").asString("").trim();
        return new ExtractedRecipe(title.isEmpty() ? text.trim() : title, ingredients);
    }

    private List<IngredientRequest> parseIngredients(JsonNode ingredientsNode) {
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

    private BigDecimal parseQuantity(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        BigDecimal quantity = node.decimalValue();
        return quantity.signum() > 0 ? quantity : null;
    }

    private String parseUnit(JsonNode node) {
        String unit = node.asString("").trim().toLowerCase(Locale.ROOT);
        return unit.isEmpty() || unit.equals("none") ? null : unit;
    }
}
