package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.agent.tools.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentService {

    private static final int MAX_TOOL_CALL_ROUNDS = 5;

    // The small local model reliably understands "it" from the history but picked add_item for "make it 3 kg",
    // turning 2 kg into 5 kg, so the add/update distinction is spelled out.
    static final String SYSTEM_PROMPT = """
            You are the shopping list assistant of PantryPal. Use the tools to read and change the shopping list,             and never claim a change you did not make with a tool.
            - add_item ADDS an amount to what is already on the list. To set, change or correct the quantity or unit             of an item that is already there ("make it 3 kg", "change the eggs to 6"), use update_item, not add_item.
            - "it" and "that" mean the item discussed most recently in this conversation.
            - If you are not sure what is on the list, call list_items first.
            After changing the list, reply with one short sentence saying what you changed.""";

    private final OllamaChatClient ollamaChatClient;
    private final List<AgentTool> agentTools;
    private final ObjectMapper objectMapper;
    private final ConversationStore conversationStore;

    public String chat(String conversationId, String userMessage) {
        Map<String, AgentTool> toolsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));

        List<OllamaTool> toolDefinitions = agentTools.stream()
                .map(tool -> OllamaTool.function(tool.name(), tool.description(), tool.parameterSchema()))
                .toList();

        List<OllamaMessage> messages = new ArrayList<>();
        messages.add(OllamaMessage.system(SYSTEM_PROMPT));
        messages.addAll(conversationStore.history(conversationId));
        messages.add(OllamaMessage.user(userMessage));

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            OllamaChatResponse response = ollamaChatClient.chat(messages, toolDefinitions);
            OllamaMessage assistantMessage = response.message();
            messages.add(assistantMessage);

            List<OllamaToolCall> toolCalls = assistantMessage.toolCalls();
            log.info("Round {}: content='{}', toolCalls={}", round, assistantMessage.content(), toolCalls);
            if (toolCalls == null || toolCalls.isEmpty()) {
                conversationStore.append(conversationId, userMessage, assistantMessage.content());
                return assistantMessage.content();
            }

            for (OllamaToolCall toolCall : toolCalls) {
                messages.add(OllamaMessage.tool(executeToolCall(toolsByName, toolCall)));
            }
        }

        throw new IllegalStateException(
                "Ollama did not produce a final answer within " + MAX_TOOL_CALL_ROUNDS + " tool-call rounds");
    }

    public void forgetConversation(String conversationId) {
        conversationStore.clear(conversationId);
    }

    private String executeToolCall(Map<String, AgentTool> toolsByName, OllamaToolCall toolCall) {
        String toolName = toolCall.function().name();
        AgentTool tool = toolsByName.get(toolName);
        log.info("Executing tool '{}' with arguments {}", toolName, toolCall.function().arguments());

        Object result;
        if (tool == null) {
            result = Map.of("error", "Unknown tool: " + toolName);
        } else {
            try {
                result = tool.execute(toolCall.function().arguments());
            } catch (RuntimeException e) {
                // Hand the failure back to the model (e.g. "No shopping list item named 'it'") so it can recover
                // or ask the user, instead of failing the whole request.
                log.warn("Tool '{}' failed: {}", toolName, e.toString());
                result = Map.of("error", String.valueOf(e.getMessage()));
            }
        }

        log.info("Tool '{}' result: {}", toolName, result);
        return objectMapper.writeValueAsString(result);
    }
}
