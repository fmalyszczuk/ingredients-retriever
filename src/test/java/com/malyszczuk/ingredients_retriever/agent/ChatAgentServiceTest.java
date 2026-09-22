package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.agent.tools.AgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAgentServiceTest {

    @Mock
    private OllamaChatClient ollamaChatClient;

    @Mock
    private AgentTool addItemTool;

    private ChatAgentService chatAgentService;

    @BeforeEach
    void setUp() {
        when(addItemTool.name()).thenReturn("add_item");
        when(addItemTool.description()).thenReturn("Adds an item");
        when(addItemTool.parameterSchema()).thenReturn(Map.of("type", "object"));

        chatAgentService = new ChatAgentService(ollamaChatClient, List.of(addItemTool), new ObjectMapper());
    }

    @Test
    void chat_returnsContent_whenNoToolCallsRequested() {
        OllamaMessage finalMessage = new OllamaMessage("assistant", "Here is your list.", null);
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", finalMessage, true));

        String reply = chatAgentService.chat("what's on my list?");

        assertEquals("Here is your list.", reply);
        verify(ollamaChatClient, times(1)).chat(any(), any());
    }

    @Test
    void chat_executesToolAndContinuesConversation_whenToolCallRequested() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs", "quantity", 2)));
        OllamaMessage toolCallMessage = new OllamaMessage("assistant", "", List.of(toolCall));
        OllamaMessage finalMessage = new OllamaMessage("assistant", "Added eggs to your list.", null);

        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", toolCallMessage, true))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", finalMessage, true));
        when(addItemTool.execute(Map.of("name", "eggs", "quantity", 2))).thenReturn(Map.of("name", "eggs"));

        String reply = chatAgentService.chat("add 2 eggs");

        assertEquals("Added eggs to your list.", reply);
        verify(addItemTool, times(1)).execute(Map.of("name", "eggs", "quantity", 2));
        verify(ollamaChatClient, times(2)).chat(any(), any());
    }

    @Test
    void chat_throws_whenToolCallLoopNeverTerminates() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs")));
        OllamaMessage toolCallMessage = new OllamaMessage("assistant", "", List.of(toolCall));
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", toolCallMessage, true));
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        assertThrows(IllegalStateException.class, () -> chatAgentService.chat("add eggs forever"));
    }
}
