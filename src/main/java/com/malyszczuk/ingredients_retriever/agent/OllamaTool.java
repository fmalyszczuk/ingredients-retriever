package com.malyszczuk.ingredients_retriever.agent;

import java.util.Map;

public record OllamaTool(String type, OllamaToolFunction function) {

    public static OllamaTool function(String name, String description, Map<String, Object> parameters) {
        return new OllamaTool("function", new OllamaToolFunction(name, description, parameters));
    }
}
