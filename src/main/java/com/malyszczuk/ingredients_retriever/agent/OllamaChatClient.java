package com.malyszczuk.ingredients_retriever.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
}
