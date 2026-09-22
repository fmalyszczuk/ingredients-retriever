package com.malyszczuk.ingredients_retriever.dto;

import java.math.BigDecimal;

public record IngredientResponse(
        Long id,
        String name,
        BigDecimal quantity,
        String unit
) {
}
