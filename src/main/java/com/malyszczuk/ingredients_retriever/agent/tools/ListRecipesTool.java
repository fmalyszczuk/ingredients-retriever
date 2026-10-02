package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListRecipesTool implements AgentTool {

    // Newest first and capped: the model's context window is small.
    static final int MAX_RECIPES = 8;

    private final RecipeService recipeService;

    @Override
    public String name() {
        return "list_recipes";
    }

    @Override
    public String description() {
        return "Lists the saved recipes (newest first) with their id and ingredients. Use it to find a recipe's id.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        List<Recipe> recipes = new ArrayList<>(recipeService.listRecipes());
        int total = recipes.size();
        List<Recipe> newestFirst = recipes.reversed().stream().limit(MAX_RECIPES).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRecipes", total);
        result.put("recipes", newestFirst.stream().map(this::toResult).toList());
        if (total > MAX_RECIPES) {
            result.put("note", "Only the " + MAX_RECIPES + " newest recipes are shown.");
        }
        return result;
    }

    private Map<String, Object> toResult(Recipe recipe) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", recipe.getId());
        result.put("title", recipe.getTitle());
        result.put("ingredients", RecipeToolSupport.describeIngredients(recipe));
        return result;
    }
}
