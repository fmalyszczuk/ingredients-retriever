package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.agent.ChatAgentService;
import com.malyszczuk.ingredients_retriever.agent.ChatResult;
import com.malyszczuk.ingredients_retriever.agent.OllamaResponseException;
import com.malyszczuk.ingredients_retriever.dto.ChatAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

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
        when(chatAgentService.chat(any(), eq("add eggs"))).thenReturn(new ChatResult("Added eggs.",
                List.of(new ChatAction("add_item", Map.of("name", "eggs"), ChatAction.OK, Map.of("name", "eggs"))), true));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"add eggs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Added eggs."))
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.shoppingListChanged").value(true))
                .andExpect(jsonPath("$.actions[0].tool").value("add_item"))
                .andExpect(jsonPath("$.actions[0].status").value("ok"))
                .andExpect(jsonPath("$.actions[0].result.name").value("eggs"));
    }

    @Test
    void chat_continuesTheGivenConversation() throws Exception {
        when(chatAgentService.chat("abc-123", "and milk")).thenReturn(new ChatResult("Added milk.", List.of(), false));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"and milk\", \"conversationId\": \"abc-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Added milk."))
                .andExpect(jsonPath("$.conversationId").value("abc-123"))
                .andExpect(jsonPath("$.shoppingListChanged").value(false))
                .andExpect(jsonPath("$.incomplete").value(false))
                .andExpect(jsonPath("$.actions").isEmpty());
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

    @Test
    void chat_returnsOkWithIncompleteFlag_andWhatRan_whenTheAssistantGaveUp() throws Exception {
        when(chatAgentService.chat(any(), any())).thenReturn(new ChatResult("Sorry, I couldn't finish that.",
                List.of(new ChatAction("add_item", Map.of("name", "eggs"), ChatAction.OK, Map.of("name", "eggs"))), true, true));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"add eggs forever\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(true))
                .andExpect(jsonPath("$.shoppingListChanged").value(true))
                .andExpect(jsonPath("$.actions[0].tool").value("add_item"));
    }

    @Test
    void chat_returnsGatewayTimeout_whenOllamaTakesTooLong() throws Exception {
        when(chatAgentService.chat(any(), any())).thenThrow(new ResourceAccessException("I/O error",
                new java.net.http.HttpTimeoutException("request timed out")));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void chat_returnsGatewayTimeout_forASocketReadTimeout() throws Exception {
        when(chatAgentService.chat(any(), any())).thenThrow(new ResourceAccessException("I/O error",
                new java.net.SocketTimeoutException("Read timed out")));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void chat_returnsServiceUnavailable_whenConnectingToOllamaTimesOut() throws Exception {
        when(chatAgentService.chat(any(), any())).thenThrow(new ResourceAccessException("I/O error",
                new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void chat_returnsBadGateway_whenOllamaReturnsNothingUsable() throws Exception {
        when(chatAgentService.chat(any(), any())).thenThrow(new OllamaResponseException("empty response"));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isBadGateway());
    }
}
