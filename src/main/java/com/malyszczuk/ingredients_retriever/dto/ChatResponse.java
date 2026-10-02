package com.malyszczuk.ingredients_retriever.dto;

import java.util.List;

/**
 * {@code shoppingListChanged} is true when at least one tool that modifies the list ran successfully, so the client
 * knows to re-fetch it. The reply text itself is written by the model and is not always accurate.
 */
public record ChatResponse(String reply, String conversationId, boolean shoppingListChanged, List<ChatAction> actions) {
}
