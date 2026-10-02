package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code conversationId} is optional: omit it to start a new conversation, send the one you got back to continue it. */
public record ChatRequest(
        @NotBlank String message,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String conversationId
) {
}
