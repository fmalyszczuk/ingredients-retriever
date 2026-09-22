package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AddShoppingListItemRequest(
        @NotBlank String name,
        BigDecimal quantity,
        String unit
) {
}
