package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.agent.tools.AgentTool;
import com.malyszczuk.ingredients_retriever.dto.ChatAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentService {

    private static final int MAX_TOOL_CALL_ROUNDS = 5;

    static final String GIVE_UP_REPLY =
            "Sorry, I couldn't finish that. Please try rephrasing it, and check the shopping list to see what changed.";

    // The small local model reliably understands "it" from the history but picked add_item for "make it 3 kg",
    // turning 2 kg into 5 kg, so the add/update distinction is spelled out. The clear confirmation is enforced in
    // code (see executeToolCall); the prompt only makes the model's wording match what happens.
    static final String SYSTEM_PROMPT = """
            You are the shopping list assistant of PantryPal. Use the tools to read and change the shopping list, and never claim a change you did not make with a tool.
            - add_item ADDS an amount to what is already on the list. To set, change or correct the quantity or unit of an item that is already there ("make it 3 kg", "change the eggs to 6"), use update_item, not add_item.
            - "it" and "that" mean the item discussed most recently in this conversation.
            - clear_shopping_list deletes everything. The first time you call it nothing is deleted yet: ask the user whether they are sure. Call it again only after they clearly say yes; if they say no, do not call it.
            - If you are not sure what is on the list, call list_items first.
            After changing the list, reply with one short sentence saying what you changed.""";

    private final OllamaChatClient ollamaChatClient;
    private final List<AgentTool> agentTools;
    private final ObjectMapper objectMapper;
    private final ConversationStore conversationStore;

    public ChatResult chat(String conversationId, String userMessage) {
        Map<String, AgentTool> toolsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));

        List<OllamaTool> toolDefinitions = agentTools.stream()
                .map(tool -> OllamaTool.function(tool.name(), tool.description(), tool.parameterSchema()))
                .toList();

        List<OllamaMessage> messages = new ArrayList<>();
        messages.add(OllamaMessage.system(SYSTEM_PROMPT));
        messages.addAll(conversationStore.history(conversationId));
        messages.add(OllamaMessage.user(userMessage));

        // A confirmation only counts in the message right after it was asked for, so these are consumed now, and only
        // if that message is itself a clear "yes": the model is not trusted to tell yes from no.
        Set<String> askedTools = conversationStore.takePendingConfirmations(conversationId);
        boolean userConfirmed = !askedTools.isEmpty() && ConfirmationReply.isConfirmation(userMessage);
        Set<String> toolsAwaitingConfirmation = new LinkedHashSet<>();
        List<ChatAction> actions = new ArrayList<>();

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            OllamaChatResponse response = ollamaChatClient.chat(messages, toolDefinitions);
            if (response == null || response.message() == null) {
                throw new OllamaResponseException("The language model (Ollama) returned an empty response");
            }
            OllamaMessage assistantMessage = response.message();
            messages.add(assistantMessage);

            List<OllamaToolCall> toolCalls = assistantMessage.toolCalls();
            log.info("Round {}: content='{}', toolCalls={}", round, assistantMessage.content(), toolCalls);
            if (toolCalls == null || toolCalls.isEmpty()) {
                conversationStore.append(conversationId, userMessage, assistantMessage.content());
                conversationStore.setPendingConfirmations(conversationId, toolsAwaitingConfirmation);
                return new ChatResult(assistantMessage.content(), actions, changedShoppingList(actions, toolsByName));
            }

            for (OllamaToolCall toolCall : toolCalls) {
                messages.add(OllamaMessage.tool(
                        executeToolCall(toolsByName, toolCall, askedTools, userConfirmed, toolsAwaitingConfirmation, actions)));
            }
        }

        // Tools may already have changed the list, so report that instead of failing the request. The turn is not
        // remembered, and nothing is left awaiting confirmation.
        log.warn("No final answer after {} tool-call rounds; giving up", MAX_TOOL_CALL_ROUNDS);
        return new ChatResult(GIVE_UP_REPLY, actions, changedShoppingList(actions, toolsByName), true);
    }

    public void forgetConversation(String conversationId) {
        conversationStore.clear(conversationId);
    }

    private String executeToolCall(Map<String, AgentTool> toolsByName, OllamaToolCall toolCall,
                                   Set<String> askedTools, boolean userConfirmed,
                                   Set<String> toolsAwaitingConfirmation, List<ChatAction> actions) {
        String toolName = toolCall.function().name();
        Map<String, Object> arguments = toolCall.function().arguments() == null ? Map.of() : toolCall.function().arguments();
        AgentTool tool = toolsByName.get(toolName);
        log.info("Executing tool '{}' with arguments {}", toolName, arguments);

        Object result;
        String status;
        if (tool == null) {
            status = ChatAction.ERROR;
            result = Map.of("error", "Unknown tool: " + toolName);
        } else if (tool.requiresConfirmation() && !askedTools.contains(toolName)) {
            // Enforced here rather than trusted to the model: nothing runs until the user has been asked and
            // has answered in a separate message.
            toolsAwaitingConfirmation.add(toolName);
            status = ChatAction.CONFIRMATION_REQUIRED;
            result = Map.of("status", "confirmation_required",
                    "instruction", "Nothing was changed. Ask the user to confirm, and call this tool again only if they say yes.");
        } else if (tool.requiresConfirmation() && !userConfirmed) {
            status = ChatAction.NOT_CONFIRMED;
            result = Map.of("status", "not_confirmed",
                    "instruction", "The user did not clearly confirm, so nothing was changed. Do not call this tool again unless they ask again.");
        } else {
            try {
                result = tool.execute(arguments);
                status = ChatAction.OK;
            } catch (RuntimeException e) {
                // Hand the failure back to the model (e.g. "No shopping list item named 'it'") so it can recover
                // or ask the user, instead of failing the whole request.
                log.warn("Tool '{}' failed: {}", toolName, e.toString());
                status = ChatAction.ERROR;
                result = Map.of("error", String.valueOf(e.getMessage()));
            }
        }

        log.info("Tool '{}' result ({}): {}", toolName, status, result);
        boolean worthReporting = !ChatAction.OK.equals(status) || tool.changesShoppingList();
        actions.add(new ChatAction(toolName, arguments, status, worthReporting ? result : null));
        return objectMapper.writeValueAsString(result);
    }

    private boolean changedShoppingList(List<ChatAction> actions, Map<String, AgentTool> toolsByName) {
        return actions.stream().anyMatch(action -> ChatAction.OK.equals(action.status())
                && toolsByName.get(action.tool()).changesShoppingList());
    }
}
