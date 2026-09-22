package com.malyszczuk.ingredients_retriever.extraction;

import java.math.BigDecimal;

public record ParsedIngredientLine(String name, BigDecimal quantity, String unit) {
}
