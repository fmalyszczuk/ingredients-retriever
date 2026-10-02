package com.malyszczuk.ingredients_retriever.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        String model,
        List<OllamaMessage> messages,
        List<OllamaTool> tools,
        boolean stream,
        Map<String, Object> format,
        Map<String, Object> options
) {
    public OllamaChatRequest(String model, List<OllamaMessage> messages, List<OllamaTool> tools, boolean stream) {
        this(model, messages, tools, stream, null, null);
    }

    public OllamaChatRequest(String model, List<OllamaMessage> messages, List<OllamaTool> tools, boolean stream,
                             Map<String, Object> format) {
        this(model, messages, tools, stream, format, null);
    }
}
