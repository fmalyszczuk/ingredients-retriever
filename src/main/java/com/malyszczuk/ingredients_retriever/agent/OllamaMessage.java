package com.malyszczuk.ingredients_retriever.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OllamaMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<OllamaToolCall> toolCalls
) {
    public static OllamaMessage user(String content) {
        return new OllamaMessage("user", content, null);
    }

    public static OllamaMessage tool(String content) {
        return new OllamaMessage("tool", content, null);
    }
}
