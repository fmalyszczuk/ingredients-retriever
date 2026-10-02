package com.malyszczuk.ingredients_retriever.dto;

import java.util.List;

/**
 * {@code shoppingListChanged} is true when at least one tool that modifies the list ran successfully, so the client
 * knows to re-fetch it. The reply text itself is written by the model and is not always accurate.
 * {@code incomplete} is true when the assistant gave up without an answer (e.g. it kept calling tools); the reply is
 * then a fixed apology, but {@code actions} and {@code shoppingListChanged} still say what ran before it gave up.
 */
public record ChatResponse(String reply, String conversationId, boolean shoppingListChanged, boolean incomplete,
                           List<ChatAction> actions) {
}
