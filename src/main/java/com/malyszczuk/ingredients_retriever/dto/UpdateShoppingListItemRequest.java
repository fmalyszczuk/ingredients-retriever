package com.malyszczuk.ingredients_retriever.dto;

import java.math.BigDecimal;

/**
 * PATCH semantics: any field left null is left unchanged on the existing item.
 * To toggle purchased, send {"purchased": true} with quantity/unit omitted.
 */
public record UpdateShoppingListItemRequest(
        Boolean purchased,
        BigDecimal quantity,
        String unit
) {
}
