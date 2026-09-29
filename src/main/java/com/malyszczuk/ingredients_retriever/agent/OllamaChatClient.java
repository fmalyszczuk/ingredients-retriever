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
        OllamaChatRequest request = new OllamaChatRequest(ollamaProperties.model(), messages, tools, false);

        return ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);
    }

    /** Asks the model for a single reply constrained to the given JSON schema and returns its raw JSON content. */
    public String chatStructured(String prompt, Map<String, Object> jsonSchema) {
        OllamaChatRequest request = new OllamaChatRequest(
                ollamaProperties.model(), List.of(OllamaMessage.user(prompt)), null, false, jsonSchema);

        OllamaChatResponse response = ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        return response == null || response.message() == null ? null : response.message().content();
    }
}
