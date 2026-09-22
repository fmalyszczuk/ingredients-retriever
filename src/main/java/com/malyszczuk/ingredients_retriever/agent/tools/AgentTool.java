package com.malyszczuk.ingredients_retriever.agent.tools;

import java.util.Map;

public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameterSchema();

    Object execute(Map<String, Object> arguments);
}
