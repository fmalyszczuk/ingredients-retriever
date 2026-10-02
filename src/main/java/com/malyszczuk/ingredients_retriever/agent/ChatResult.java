package com.malyszczuk.ingredients_retriever.agent;

import com.malyszczuk.ingredients_retriever.dto.ChatAction;

import java.util.List;

public record ChatResult(String reply, List<ChatAction> actions, boolean shoppingListChanged) {
}
