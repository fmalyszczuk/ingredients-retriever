package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeleteRecipeTool implements AgentTool {

    private final RecipeService recipeService;

    @Override
    public String name() {
        return "delete_recipe";
    }

    @Override
    public String description() {
        return "Deletes a SAVED RECIPE, identified by its title (or id). Not for shopping list items: use "
                + "remove_item for those. The first call only asks the user for confirmation and deletes nothing; "
                + "call it again with the same recipe after the user confirms.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string",
                                "description", "The recipe's title, or a distinctive part of it, e.g. 'carbonara'"),
                        "id", Map.of("type", "number", "description", "The recipe id, if known (from list_recipes)"),
                        "remove_from_shopping_list", Map.of("type", "boolean",
                                "description", "Also take the recipe's ingredients off the shopping list. Default false.")
                ),
                "required", List.of()
        );
    }

    @Override
    public boolean changesShoppingList() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    /** A confirmation to delete one recipe must not be usable to delete another. */
    @Override
    public String confirmationTarget(Map<String, Object> arguments) {
        try {
            return String.valueOf(resolve(arguments).getId());
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public String confirmationDescription(Map<String, Object> arguments) {
        try {
            Recipe recipe = resolve(arguments);
            return "Delete the saved recipe '" + recipe.getTitle() + "' (id " + recipe.getId() + ")";
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Recipe recipe = resolve(arguments);
        boolean removeFromShoppingList = Boolean.parseBoolean(String.valueOf(arguments.get("remove_from_shopping_list")));

        recipeService.deleteRecipe(recipe.getId(), removeFromShoppingList);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedRecipeId", recipe.getId());
        result.put("title", recipe.getTitle());
        result.put("removedIngredientsFromShoppingList", removeFromShoppingList);
        return result;
    }

    private Recipe resolve(Map<String, Object> arguments) {
        Object id = arguments.get("id");
        if (id != null && !id.toString().isBlank()) {
            return recipeService.getRecipe(RecipeToolSupport.toId(id));
        }

        Object title = arguments.get("title");
        String wanted = title == null ? "" : title.toString().trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Give the title of the recipe to delete");
        }

        List<Recipe> recipes = recipeService.listRecipes();
        List<Recipe> exact = recipes.stream().filter(r -> r.getTitle().toLowerCase(Locale.ROOT).equals(wanted)).toList();
        List<Recipe> candidates = exact.isEmpty()
                ? recipes.stream().filter(r -> r.getTitle().toLowerCase(Locale.ROOT).contains(wanted)).toList()
                : exact;

        if (candidates.isEmpty()) {
            throw new NoSuchElementException("No saved recipe matches '" + title + "'. Use list_recipes to see them.");
        }
        if (candidates.size() > 1) {
            String options = candidates.stream()
                    .map(r -> "[" + r.getId() + "] " + r.getTitle())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Several recipes match '" + title + "': " + options
                    + ". Ask the user which one.");
        }
        return candidates.getFirst();
    }
}
