package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.agent.ChatAgentService;
import com.malyszczuk.ingredients_retriever.dto.ChatRequest;
import com.malyszczuk.ingredients_retriever.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatAgentService chatAgentService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatAgentService.chat(request.message()));
    }
}
