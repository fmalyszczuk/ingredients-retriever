package com.malyszczuk.ingredients_retriever.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory chat history per conversation. It only keeps the user's messages and the assistant's final replies
 * (not tool calls or their results), and both the history length and the number of conversations are capped,
 * because the local model's context window is small and the shopping list itself is the source of truth.
 * Everything is lost on restart, like the database.
 */
@Component
public class ConversationStore {

    private static final int DEFAULT_MAX_MESSAGES = 16;
    private static final int DEFAULT_MAX_CONVERSATIONS = 200;

    private final int maxMessages;
    private final Map<String, List<OllamaMessage>> conversations;

    public ConversationStore() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_CONVERSATIONS);
    }

    ConversationStore(int maxMessages, int maxConversations) {
        this.maxMessages = maxMessages;
        // Access order: reading or writing a conversation makes it the most recently used one.
        this.conversations = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<OllamaMessage>> eldest) {
                return size() > maxConversations;
            }
        };
    }

    public synchronized List<OllamaMessage> history(String conversationId) {
        List<OllamaMessage> history = conversations.get(conversationId);
        return history == null ? List.of() : List.copyOf(history);
    }

    public synchronized void append(String conversationId, String userMessage, String assistantReply) {
        List<OllamaMessage> history = conversations.computeIfAbsent(conversationId, id -> new ArrayList<>());
        history.add(OllamaMessage.user(userMessage));
        history.add(OllamaMessage.assistant(assistantReply == null ? "" : assistantReply));

        // Drop the oldest messages in whole turns so the history never starts with a dangling assistant reply.
        while (history.size() > maxMessages && history.size() > 2) {
            history.removeFirst();
            history.removeFirst();
        }
    }

    public synchronized void clear(String conversationId) {
        conversations.remove(conversationId);
    }
}
