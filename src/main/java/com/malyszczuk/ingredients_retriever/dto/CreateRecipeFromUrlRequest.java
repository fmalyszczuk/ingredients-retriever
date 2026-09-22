package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateRecipeFromUrlRequest(@NotBlank @URL String url) {
}
