package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.agent.ChatAgentService;
import com.malyszczuk.ingredients_retriever.dto.ChatRequest;
import com.malyszczuk.ingredients_retriever.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatAgentService chatAgentService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        String reply = chatAgentService.chat(conversationId, request.message());
        return new ChatResponse(reply, conversationId);
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgetConversation(@PathVariable String conversationId) {
        chatAgentService.forgetConversation(conversationId);
    }
}
