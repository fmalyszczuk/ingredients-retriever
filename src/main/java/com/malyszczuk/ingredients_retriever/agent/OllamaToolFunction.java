package com.malyszczuk.ingredients_retriever.agent;

import java.util.Map;

public record OllamaToolFunction(String name, String description, Map<String, Object> parameters) {
}
