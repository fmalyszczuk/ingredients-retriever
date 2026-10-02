package com.malyszczuk.ingredients_retriever.agent.tools;

import java.util.Map;

public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameterSchema();

    Object execute(Map<String, Object> arguments);

    /** Whether a successful call modifies the shopping list, so clients know to refresh it. */
    default boolean changesShoppingList() {
        return false;
    }

    /**
     * Whether the user has to confirm before the tool really runs. The chat service enforces this: the first call
     * only asks for confirmation, and the tool executes only if it is called again in the user's next message.
     */
    default boolean requiresConfirmation() {
        return false;
    }
}
