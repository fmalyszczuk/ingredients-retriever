package com.malyszczuk.ingredients_retriever.dto;

import java.math.BigDecimal;

public record ShoppingListItemResponse(
        Long id,
        String name,
        BigDecimal quantity,
        String unit,
        boolean purchased
) {
}
