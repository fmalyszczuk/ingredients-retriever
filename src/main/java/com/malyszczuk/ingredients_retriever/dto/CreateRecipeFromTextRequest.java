package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRecipeFromTextRequest(@NotBlank @Size(max = 200) String text) {
}
