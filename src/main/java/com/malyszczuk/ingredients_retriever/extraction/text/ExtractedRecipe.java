package com.malyszczuk.ingredients_retriever.extraction.text;

import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;

import java.util.List;

public record ExtractedRecipe(String title, List<IngredientRequest> ingredients) {
}
