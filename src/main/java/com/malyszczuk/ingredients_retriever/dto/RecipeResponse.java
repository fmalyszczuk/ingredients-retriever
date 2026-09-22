package com.malyszczuk.ingredients_retriever.dto;

import java.time.Instant;
import java.util.List;

public record RecipeResponse(
        Long id,
        String title,
        String sourceType,
        String sourceReference,
        Instant createdAt,
        List<IngredientResponse> ingredients
) {
}
