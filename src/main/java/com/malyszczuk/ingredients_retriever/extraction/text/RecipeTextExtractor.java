package com.malyszczuk.ingredients_retriever.extraction.text;

import com.malyszczuk.ingredients_retriever.agent.OllamaChatClient;
import com.malyszczuk.ingredients_retriever.extraction.llm.LlmRecipeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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

    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;

    public ExtractedRecipe extract(String text) {
        String dish = text.trim();
        String json = ollamaChatClient.chatStructured(PROMPT_TEMPLATE.formatted(dish), LlmRecipeSupport.SCHEMA);
        return LlmRecipeSupport.parse(objectMapper, json, "'" + dish + "'", dish);
    }
}
