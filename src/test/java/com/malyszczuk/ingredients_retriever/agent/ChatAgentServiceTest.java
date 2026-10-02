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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private ConversationStore conversationStore;

    @BeforeEach
    void setUp() {
        when(addItemTool.name()).thenReturn("add_item");
        when(addItemTool.description()).thenReturn("Adds an item");
        when(addItemTool.parameterSchema()).thenReturn(Map.of("type", "object"));

        conversationStore = new ConversationStore();
        chatAgentService = new ChatAgentService(ollamaChatClient, List.of(addItemTool), new ObjectMapper(), conversationStore);
    }

    @Test
    void chat_returnsContent_whenNoToolCallsRequested() {
        OllamaMessage finalMessage = new OllamaMessage("assistant", "Here is your list.", null);
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", finalMessage, true));

        String reply = chatAgentService.chat("c1", "what's on my list?");

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

        String reply = chatAgentService.chat("c1", "add 2 eggs");

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

        assertThrows(IllegalStateException.class, () -> chatAgentService.chat("c1", "add eggs forever"));
    }

    @Test
    void chat_sendsEarlierTurnsOfTheSameConversationToTheModel() {
        List<List<OllamaMessage>> sentToModel = new java.util.ArrayList<>();
        when(ollamaChatClient.chat(any(), any())).thenAnswer(invocation -> {
            sentToModel.add(List.copyOf(invocation.getArgument(0)));
            return new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "reply " + sentToModel.size(), null), true);
        });

        chatAgentService.chat("c1", "add eggs");
        chatAgentService.chat("c1", "make it 6");

        assertEquals(List.of("system", "user"), sentToModel.get(0).stream().map(OllamaMessage::role).toList());
        List<OllamaMessage> second = sentToModel.get(1);
        assertEquals(List.of("system", "user", "assistant", "user"), second.stream().map(OllamaMessage::role).toList());
        assertEquals(ChatAgentService.SYSTEM_PROMPT, second.get(0).content());
        assertEquals("add eggs", second.get(1).content());
        assertEquals("reply 1", second.get(2).content());
        assertEquals("make it 6", second.get(3).content());
    }

    @Test
    void chat_keepsConversationsIsolated() {
        List<List<OllamaMessage>> sentToModel = new java.util.ArrayList<>();
        when(ollamaChatClient.chat(any(), any())).thenAnswer(invocation -> {
            sentToModel.add(List.copyOf(invocation.getArgument(0)));
            return new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "ok", null), true);
        });

        chatAgentService.chat("a", "first");
        chatAgentService.chat("b", "second");

        assertEquals(List.of("system", "user"), sentToModel.get(1).stream().map(OllamaMessage::role).toList());
    }

    @Test
    void chat_storesOnlyUserAndFinalReply_notToolTraffic() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs")));
        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "", List.of(toolCall)), true))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "Added eggs.", null), true));
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        chatAgentService.chat("c1", "add eggs");

        List<OllamaMessage> stored = conversationStore.history("c1");
        assertEquals(List.of("user", "assistant"), stored.stream().map(OllamaMessage::role).toList());
        assertEquals("Added eggs.", stored.get(1).content());
    }

    @Test
    void chat_doesNotRememberATurnThatFailed() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs")));
        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "", List.of(toolCall)), true));
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        assertThrows(IllegalStateException.class, () -> chatAgentService.chat("c1", "add eggs forever"));

        assertTrue(conversationStore.history("c1").isEmpty());
    }

    @Test
    void forgetConversation_clearsItsHistory() {
        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "ok", null), true));
        chatAgentService.chat("c1", "hello");

        chatAgentService.forgetConversation("c1");

        assertTrue(conversationStore.history("c1").isEmpty());
    }

    @Test
    void chat_givesAToolFailureBackToTheModel_insteadOfFailingTheRequest() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "it")));
        List<List<OllamaMessage>> sentToModel = new java.util.ArrayList<>();
        when(ollamaChatClient.chat(any(), any())).thenAnswer(invocation -> {
            sentToModel.add(List.copyOf(invocation.getArgument(0)));
            OllamaMessage message = sentToModel.size() == 1
                    ? new OllamaMessage("assistant", "", List.of(toolCall))
                    : new OllamaMessage("assistant", "Which item do you mean?", null);
            return new OllamaChatResponse("llama3.2", "now", message, true);
        });
        when(addItemTool.execute(any())).thenThrow(new java.util.NoSuchElementException("No shopping list item named 'it'"));

        String reply = chatAgentService.chat("c1", "make it 5 kg");

        assertEquals("Which item do you mean?", reply);
        OllamaMessage toolResult = sentToModel.get(1).getLast();
        assertEquals("tool", toolResult.role());
        assertTrue(toolResult.content().contains("No shopping list item named 'it'"));
    }
}
