package com.malyszczuk.ingredients_retriever.extraction.url;

import java.util.List;

public record RawRecipe(String title, List<String> ingredientLines) {
}
