package com.malyszczuk.ingredients_retriever.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<OllamaToolCall> toolCalls,
        List<String> images
) {
    public OllamaMessage(String role, String content, List<OllamaToolCall> toolCalls) {
        this(role, content, toolCalls, null);
    }

    public static OllamaMessage user(String content) {
        return new OllamaMessage("user", content, null);
    }

    public static OllamaMessage userWithImages(String content, List<byte[]> images) {
        List<String> encoded = images.stream().map(image -> Base64.getEncoder().encodeToString(image)).toList();
        return new OllamaMessage("user", content, null, encoded);
    }

    public static OllamaMessage tool(String content) {
        return new OllamaMessage("tool", content, null);
    }
}
