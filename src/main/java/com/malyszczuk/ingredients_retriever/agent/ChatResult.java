package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.dto.ChatAction;

import java.util.List;

/** {@code incomplete} means the assistant gave up before producing an answer (the reply is then a fixed apology). */
public record ChatResult(String reply, List<ChatAction> actions, boolean shoppingListChanged, boolean incomplete) {

    public ChatResult(String reply, List<ChatAction> actions, boolean shoppingListChanged) {
        this(reply, actions, shoppingListChanged, false);
    }
}
