package com.malyszczuk.ingredients_retriever.agent;

import java.util.List;

public record OllamaChatRequest(String model, List<OllamaMessage> messages, List<OllamaTool> tools, boolean stream) {
}
