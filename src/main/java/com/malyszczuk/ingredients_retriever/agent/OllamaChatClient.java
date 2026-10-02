package com.malyszczuk.ingredients_retriever.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaChatClient {

    private final RestClient ollamaRestClient;
    private final OllamaProperties ollamaProperties;

    public OllamaChatResponse chat(List<OllamaMessage> messages, List<OllamaTool> tools) {
        return send(new OllamaChatRequest(ollamaProperties.model(), messages, tools, false));
    }

    /** Asks the model for a single reply constrained to the given JSON schema and returns its raw JSON content. */
    public String chatStructured(String prompt, Map<String, Object> jsonSchema) {
        return contentOf(send(new OllamaChatRequest(
                ollamaProperties.model(), List.of(OllamaMessage.user(prompt)), null, false, jsonSchema)));
    }

    /** Same as {@link #chatStructured}, but shows the given images to the configured vision model. */
    public String chatStructuredWithImages(String prompt, List<byte[]> images, Map<String, Object> jsonSchema) {
        return contentOf(send(new OllamaChatRequest(
                ollamaProperties.visionModel(), List.of(OllamaMessage.userWithImages(prompt, images)), null, false, jsonSchema)));
    }

    private OllamaChatResponse send(OllamaChatRequest request) {
        return ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);
    }

    private String contentOf(OllamaChatResponse response) {
        return response == null || response.message() == null ? null : response.message().content();
    }
}
