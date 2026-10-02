package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AddRecipeTool implements AgentTool {

    private final RecipeService recipeService;

    @Override
    public String name() {
        return "add_recipe";
    }

    @Override
    public String description() {
        return "Saves a recipe and puts its ingredients on the shopping list. Give either the URL of a recipe page "
                + "or a dish name such as 'spaghetti carbonara'. For a dish name the ingredients are suggested by AI, "
                + "not taken from a real recipe.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "recipe", Map.of("type", "string",
                                "description", "A recipe page URL (http or https) or a dish name, e.g. 'pancakes'")
                ),
                "required", List.of("recipe")
        );
    }

    @Override
    public boolean changesShoppingList() {
        return true;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Object argument = arguments.get("recipe");
        String input = argument == null ? "" : argument.toString().trim();
        if (input.isEmpty()) {
            throw new IllegalArgumentException("Give a dish name or a recipe page URL");
        }

        boolean fromUrl = isUrl(input);
        Recipe recipe = fromUrl ? recipeService.addRecipeFromUrl(input) : recipeService.addRecipeFromText(input);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipeId", recipe.getId());
        result.put("title", recipe.getTitle());
        result.put("ingredients", RecipeToolSupport.describeIngredients(recipe));
        result.put("addedToShoppingList", true);
        if (!fromUrl) {
            result.put("note", "These ingredients were suggested by AI from the dish name, not from a real recipe.");
        }
        return result;
    }

    private boolean isUrl(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
