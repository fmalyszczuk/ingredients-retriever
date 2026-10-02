package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.agent.tools.AgentTool;
import com.malyszczuk.ingredients_retriever.dto.ChatAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        lenient().when(addItemTool.name()).thenReturn("add_item");
        lenient().when(addItemTool.description()).thenReturn("Adds an item");
        lenient().when(addItemTool.parameterSchema()).thenReturn(Map.of("type", "object"));

        conversationStore = new ConversationStore();
        chatAgentService = new ChatAgentService(ollamaChatClient, List.of(addItemTool), new ObjectMapper(), conversationStore);
    }

    @Test
    void chat_returnsContent_whenNoToolCallsRequested() {
        OllamaMessage finalMessage = new OllamaMessage("assistant", "Here is your list.", null);
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", finalMessage, true));

        String reply = chatAgentService.chat("c1", "what's on my list?").reply();

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

        String reply = chatAgentService.chat("c1", "add 2 eggs").reply();

        assertEquals("Added eggs to your list.", reply);
        verify(addItemTool, times(1)).execute(Map.of("name", "eggs", "quantity", 2));
        verify(ollamaChatClient, times(2)).chat(any(), any());
    }

    @Test
    void chat_givesUpGracefully_whenToolCallLoopNeverTerminates() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs")));
        OllamaMessage toolCallMessage = new OllamaMessage("assistant", "", List.of(toolCall));
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", toolCallMessage, true));
        when(addItemTool.changesShoppingList()).thenReturn(true);
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        ChatResult result = chatAgentService.chat("c1", "add eggs forever");

        assertTrue(result.incomplete());
        assertEquals(ChatAgentService.GIVE_UP_REPLY, result.reply());
        // The tools did run, so the response must still say the list changed.
        assertTrue(result.shoppingListChanged());
        assertEquals(5, result.actions().size());
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
    void chat_doesNotRememberATurnTheAssistantGaveUpOn() {
        OllamaToolCall toolCall = new OllamaToolCall(new OllamaToolCallFunction("add_item", Map.of("name", "eggs")));
        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "", List.of(toolCall)), true));
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        ChatResult result = chatAgentService.chat("c1", "add eggs forever");

        assertTrue(result.incomplete());
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

        String reply = chatAgentService.chat("c1", "make it 5 kg").reply();

        assertEquals("Which item do you mean?", reply);
        OllamaMessage toolResult = sentToModel.get(1).getLast();
        assertEquals("tool", toolResult.role());
        assertTrue(toolResult.content().contains("No shopping list item named 'it'"));
    }

    private AgentTool toolMock(String name, boolean changesList, boolean needsConfirmation) {
        AgentTool tool = org.mockito.Mockito.mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name);
        when(tool.parameterSchema()).thenReturn(Map.of("type", "object"));
        lenient().when(tool.changesShoppingList()).thenReturn(changesList);
        lenient().when(tool.requiresConfirmation()).thenReturn(needsConfirmation);
        return tool;
    }

    private OllamaChatResponse calls(String toolName) {
        OllamaToolCall call = new OllamaToolCall(new OllamaToolCallFunction(toolName, Map.of()));
        return new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", "", List.of(call)), true);
    }

    private OllamaChatResponse says(String text) {
        return new OllamaChatResponse("llama3.2", "now", new OllamaMessage("assistant", text, null), true);
    }

    private ChatAgentService serviceWith(AgentTool... tools) {
        return new ChatAgentService(ollamaChatClient, List.of(tools), new ObjectMapper(), conversationStore);
    }

    @Test
    void chat_reportsTheToolsThatRan_andThatTheListChanged() {
        when(addItemTool.changesShoppingList()).thenReturn(true);
        when(ollamaChatClient.chat(any(), any())).thenReturn(calls("add_item"), says("Added."));
        when(addItemTool.execute(any())).thenReturn(Map.of("name", "eggs"));

        ChatResult result = chatAgentService.chat("c1", "add eggs");

        assertTrue(result.shoppingListChanged());
        assertEquals(1, result.actions().size());
        ChatAction action = result.actions().getFirst();
        assertEquals("add_item", action.tool());
        assertEquals(ChatAction.OK, action.status());
        assertEquals(Map.of("name", "eggs"), action.result());
    }

    @Test
    void chat_doesNotFlagAChange_forReadOnlyTools_andOmitsTheirResult() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(calls("add_item"), says("Here it is."));
        when(addItemTool.execute(any())).thenReturn(List.of(Map.of("name", "eggs")));

        ChatResult result = chatAgentService.chat("c1", "what is on my list?");

        assertFalse(result.shoppingListChanged());
        assertEquals(ChatAction.OK, result.actions().getFirst().status());
        assertNull(result.actions().getFirst().result());
    }

    @Test
    void chat_reportsAFailedToolAsAnError_withoutFlaggingAChange() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(calls("add_item"), says("Which item?"));
        when(addItemTool.execute(any())).thenThrow(new java.util.NoSuchElementException("No shopping list item named 'it'"));

        ChatResult result = chatAgentService.chat("c1", "make it 5 kg");

        assertFalse(result.shoppingListChanged());
        ChatAction action = result.actions().getFirst();
        assertEquals(ChatAction.ERROR, action.status());
        assertEquals(Map.of("error", "No shopping list item named 'it'"), action.result());
    }

    @Test
    void chat_reportsAnUnknownToolAsAnError() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(calls("does_not_exist"), says("Sorry."));

        ChatResult result = chatAgentService.chat("c1", "do something");

        assertEquals(ChatAction.ERROR, result.actions().getFirst().status());
        assertFalse(result.shoppingListChanged());
    }

    @Test
    void chat_hasNoActions_whenTheModelAnswersDirectly() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(says("Hello!"));

        ChatResult result = chatAgentService.chat("c1", "hi");

        assertTrue(result.actions().isEmpty());
        assertFalse(result.shoppingListChanged());
    }

    @Test
    void chat_asksForConfirmationFirst_andRunsTheToolOnlyWhenCalledAgainInTheNextMessage() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        when(clear.execute(any())).thenReturn(Map.of("cleared", true));
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any()))
                .thenReturn(calls("clear_shopping_list"), says("Are you sure?"))
                .thenReturn(calls("clear_shopping_list"), says("Cleared."));

        ChatResult first = service.chat("c1", "clear my list");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.CONFIRMATION_REQUIRED, first.actions().getFirst().status());
        assertFalse(first.shoppingListChanged());

        ChatResult second = service.chat("c1", "yes");

        verify(clear, times(1)).execute(any());
        assertEquals(ChatAction.OK, second.actions().getFirst().status());
        assertTrue(second.shoppingListChanged());
    }

    @Test
    void chat_requiresConfirmationAgain_afterAConfirmationHasBeenUsed() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        when(clear.execute(any())).thenReturn(Map.of("cleared", true));
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Sure?"),
                calls("clear_shopping_list"), says("Cleared."),
                calls("clear_shopping_list"), says("Sure?"));

        service.chat("c1", "clear");
        service.chat("c1", "yes");
        ChatResult third = service.chat("c1", "clear again");

        verify(clear, times(1)).execute(any());
        assertEquals(ChatAction.CONFIRMATION_REQUIRED, third.actions().getFirst().status());
    }

    @Test
    void chat_letsAConfirmationExpire_ifTheNextMessageDoesNotUseIt() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Sure?"),
                says("Okay, what else?"),
                calls("clear_shopping_list"), says("Sure?"));

        service.chat("c1", "clear");
        service.chat("c1", "actually, what is on the list?");
        ChatResult third = service.chat("c1", "ok clear it");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.CONFIRMATION_REQUIRED, third.actions().getFirst().status());
    }

    @Test
    void chat_doesNotLetAToolConfirmItselfWithinOneMessage() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), calls("clear_shopping_list"), says("Sure?"));

        ChatResult result = service.chat("c1", "clear");

        verify(clear, never()).execute(any());
        assertEquals(2, result.actions().size());
        assertTrue(result.actions().stream().allMatch(a -> ChatAction.CONFIRMATION_REQUIRED.equals(a.status())));
    }

    @Test
    void chat_keepsConfirmationsPerConversation() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Sure?"),
                calls("clear_shopping_list"), says("Sure?"));

        service.chat("a", "clear");
        ChatResult other = service.chat("b", "yes");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.CONFIRMATION_REQUIRED, other.actions().getFirst().status());
    }

    @Test
    void forgetConversation_alsoForgetsAPendingConfirmation() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Sure?"),
                calls("clear_shopping_list"), says("Sure?"));

        service.chat("c1", "clear");
        service.forgetConversation("c1");
        service.chat("c1", "yes");

        verify(clear, never()).execute(any());
    }

    @Test
    void chat_doesNotRunTheTool_whenTheModelCallsItAfterTheUserSaidNo() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Are you sure?"),
                calls("clear_shopping_list"), says("The list is cleared."));

        service.chat("c1", "clear my list");
        ChatResult declined = service.chat("c1", "No, never mind.");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.NOT_CONFIRMED, declined.actions().getFirst().status());
        assertFalse(declined.shoppingListChanged());
    }

    @Test
    void chat_doesNotTreatAQualifiedYesAsConfirmation() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Are you sure?"),
                calls("clear_shopping_list"), says("Done."));

        service.chat("c1", "clear my list");
        ChatResult result = service.chat("c1", "yes, but only the milk");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.NOT_CONFIRMED, result.actions().getFirst().status());
    }

    @Test
    void chat_asksAgain_afterAnAnswerThatWasNotAYes() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        when(ollamaChatClient.chat(any(), any())).thenReturn(
                calls("clear_shopping_list"), says("Are you sure?"),
                calls("clear_shopping_list"), says("Okay, not clearing."),
                calls("clear_shopping_list"), says("Are you sure?"));

        service.chat("c1", "clear my list");
        service.chat("c1", "no");
        ChatResult third = service.chat("c1", "yes");

        verify(clear, never()).execute(any());
        assertEquals(ChatAction.CONFIRMATION_REQUIRED, third.actions().getFirst().status());
    }

    @Test
    void chat_doesNotLeaveAConfirmationPending_whenTheAssistantGivesUp() {
        AgentTool clear = toolMock("clear_shopping_list", true, true);
        ChatAgentService service = serviceWith(clear);
        // Round after round of confirmation requests, never a final answer.
        when(ollamaChatClient.chat(any(), any())).thenReturn(calls("clear_shopping_list"));

        ChatResult gaveUp = service.chat("c1", "clear my list");

        assertTrue(gaveUp.incomplete());
        assertTrue(conversationStore.takePendingConfirmations("c1").isEmpty());
        verify(clear, never()).execute(any());
    }

    @Test
    void chat_throwsAClearError_whenOllamaReturnsNoResponse() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(null);

        assertThrows(OllamaResponseException.class, () -> chatAgentService.chat("c1", "hi"));
    }

    @Test
    void chat_throwsAClearError_whenOllamaReturnsNoMessage() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(new OllamaChatResponse("llama3.2", "now", null, true));

        assertThrows(OllamaResponseException.class, () -> chatAgentService.chat("c1", "hi"));
        assertTrue(conversationStore.history("c1").isEmpty());
    }

    @Test
    void chat_reportsACompleteTurnAsNotIncomplete() {
        when(ollamaChatClient.chat(any(), any())).thenReturn(says("Hello!"));

        assertFalse(chatAgentService.chat("c1", "hi").incomplete());
    }
}
