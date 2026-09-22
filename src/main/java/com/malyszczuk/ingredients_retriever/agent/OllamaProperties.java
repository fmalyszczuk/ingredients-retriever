package com.malyszczuk.ingredients_retriever.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pantrypal.ollama")
public record OllamaProperties(String baseUrl, String model) {

    public OllamaProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        if (model == null || model.isBlank()) {
            model = "llama3.2";
        }
    }
}
