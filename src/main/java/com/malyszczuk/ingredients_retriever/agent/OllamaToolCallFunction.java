package com.malyszczuk.ingredients_retriever.agent;

import java.util.Map;

public record OllamaToolCallFunction(String name, Map<String, Object> arguments) {
}
