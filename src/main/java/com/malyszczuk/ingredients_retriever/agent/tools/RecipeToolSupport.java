package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;

import java.math.BigDecimal;
import java.util.List;

/** Compact, model-friendly descriptions of recipes: the model's context window is small. */
final class RecipeToolSupport {

    private static final int MAX_INGREDIENTS_SHOWN = 30;

    private RecipeToolSupport() {
    }

    static List<String> describeIngredients(Recipe recipe) {
        return recipe.getIngredients().stream()
                .limit(MAX_INGREDIENTS_SHOWN)
                .map(RecipeToolSupport::describe)
                .toList();
    }

    // "200 g flour", "2 eggs", or just "salt" when there is no quantity.
    private static String describe(Ingredient ingredient) {
        StringBuilder text = new StringBuilder();
        if (ingredient.getQuantity() != null) {
            text.append(ingredient.getQuantity().stripTrailingZeros().toPlainString()).append(' ');
        }
        if (ingredient.getUnit() != null && !ingredient.getUnit().isBlank()) {
            text.append(ingredient.getUnit()).append(' ');
        }
        return text.append(ingredient.getName()).toString();
    }

    /** Accepts 3, 3.0 and "3" (the model is not consistent about numbers) but not 3.5 or text. */
    static long toId(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("The recipe id is required");
        }
        try {
            return new BigDecimal(value.toString().trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("The recipe id must be a whole number, but was '" + value + "'");
        }
    }
}
