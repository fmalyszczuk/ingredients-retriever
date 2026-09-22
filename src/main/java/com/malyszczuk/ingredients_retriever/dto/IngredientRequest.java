package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record IngredientRequest(
        @NotBlank String name,
        BigDecimal quantity,
        String unit
) {
}
