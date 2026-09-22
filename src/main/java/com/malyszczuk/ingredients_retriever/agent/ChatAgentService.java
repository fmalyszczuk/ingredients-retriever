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

    private final OllamaChatClient ollamaChatClient;
    private final List<AgentTool> agentTools;
    private final ObjectMapper objectMapper;

    public String chat(String userMessage) {
        Map<String, AgentTool> toolsByName = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));

        List<OllamaTool> toolDefinitions = agentTools.stream()
                .map(tool -> OllamaTool.function(tool.name(), tool.description(), tool.parameterSchema()))
                .toList();

        List<OllamaMessage> messages = new ArrayList<>();
        messages.add(OllamaMessage.user(userMessage));

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            OllamaChatResponse response = ollamaChatClient.chat(messages, toolDefinitions);
            OllamaMessage assistantMessage = response.message();
            messages.add(assistantMessage);

            List<OllamaToolCall> toolCalls = assistantMessage.toolCalls();
            log.info("Round {}: content='{}', toolCalls={}", round, assistantMessage.content(), toolCalls);
            if (toolCalls == null || toolCalls.isEmpty()) {
                return assistantMessage.content();
            }

            for (OllamaToolCall toolCall : toolCalls) {
                messages.add(OllamaMessage.tool(executeToolCall(toolsByName, toolCall)));
            }
        }

        throw new IllegalStateException(
                "Ollama did not produce a final answer within " + MAX_TOOL_CALL_ROUNDS + " tool-call rounds");
    }

    private String executeToolCall(Map<String, AgentTool> toolsByName, OllamaToolCall toolCall) {
        String toolName = toolCall.function().name();
        AgentTool tool = toolsByName.get(toolName);
        log.info("Executing tool '{}' with arguments {}", toolName, toolCall.function().arguments());

        Object result = (tool != null)
                ? tool.execute(toolCall.function().arguments())
                : Map.of("error", "Unknown tool: " + toolName);

        log.info("Tool '{}' result: {}", toolName, result);
        return objectMapper.writeValueAsString(result);
    }
}
