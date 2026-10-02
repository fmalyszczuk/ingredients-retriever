package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.agent.ChatAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatAgentService chatAgentService;

    @Test
    void chat_startsANewConversationAndReturnsItsId_whenNoneIsGiven() throws Exception {
        when(chatAgentService.chat(any(), eq("add eggs"))).thenReturn("Added eggs.");

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"add eggs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Added eggs."))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void chat_continuesTheGivenConversation() throws Exception {
        when(chatAgentService.chat("abc-123", "and milk")).thenReturn("Added milk.");

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"and milk\", \"conversationId\": \"abc-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Added milk."))
                .andExpect(jsonPath("$.conversationId").value("abc-123"));
    }

    @Test
    void chat_rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_rejectsMalformedConversationId() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\", \"conversationId\": \"not valid!\"}"))
                .andExpect(status().isBadRequest());

        verify(chatAgentService, never()).chat(any(), any());
    }

    @Test
    void chat_returnsServiceUnavailable_whenOllamaIsDown() throws Exception {
        when(chatAgentService.chat(any(), any())).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void forgetConversation_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/chat/abc-123"))
                .andExpect(status().isNoContent());

        verify(chatAgentService).forgetConversation("abc-123");
    }
}
