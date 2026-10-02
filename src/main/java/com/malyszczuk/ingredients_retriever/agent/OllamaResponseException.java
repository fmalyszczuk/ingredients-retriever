package com.malyszczuk.ingredients_retriever.agent;

/** Ollama answered, but not with anything usable (e.g. a response without a message). */
public class OllamaResponseException extends RuntimeException {

    public OllamaResponseException(String message) {
        super(message);
    }
}
