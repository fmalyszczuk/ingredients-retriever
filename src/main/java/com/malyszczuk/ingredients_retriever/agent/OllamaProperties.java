package com.malyszczuk.ingredients_retriever.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pantrypal.ollama")
public record OllamaProperties(String baseUrl, String model, String visionModel, Double chatTemperature,
                               Duration connectTimeout, Duration readTimeout) {

    public OllamaProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        if (model == null || model.isBlank()) {
            model = "llama3.2";
        }
        if (visionModel == null || visionModel.isBlank()) {
            visionModel = "gemma3:4b";
        }
        // At Ollama's default temperature the small model sometimes wrote a tool call as text instead of making it.
        if (chatTemperature == null) {
            chatTemperature = 0.0;
        }
        // Reading is generous because the first request after Ollama starts has to load the model (about a minute).
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(180);
        }
    }
}
