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

    /**
     * What a confirmation applies to, for tools that act on a specific thing (e.g. the id of the recipe to delete).
     * A confirmation only covers the same tool with the same target, so "yes" to deleting one recipe can't be used
     * to delete another. Tools with nothing to target return an empty string.
     */
    default String confirmationTarget(Map<String, Object> arguments) {
        return "";
    }

    /** A short description of exactly what would happen, shown to the model so it asks the user a precise question. */
    default String confirmationDescription(Map<String, Object> arguments) {
        return "";
    }
}
