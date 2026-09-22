package com.malyszczuk.ingredients_retriever.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OllamaChatResponse(
        String model,
        @JsonProperty("created_at") String createdAt,
        OllamaMessage message,
        boolean done
) {
}
