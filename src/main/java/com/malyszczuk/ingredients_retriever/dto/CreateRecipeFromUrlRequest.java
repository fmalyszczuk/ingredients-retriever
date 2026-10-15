package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateRecipeFromUrlRequest(@NotBlank @Size(max = 2048) @URL String url) {
}
