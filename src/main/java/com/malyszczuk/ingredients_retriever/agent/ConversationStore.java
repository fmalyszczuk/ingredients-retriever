package com.malyszczuk.ingredients_retriever.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory chat state per conversation: the history, and which tools are waiting for the user's confirmation.
 * The history only keeps the user's messages and the assistant's final replies (not tool calls or their results),
 * and both its length and the number of conversations are capped, because the local model's context window is small
 * and the shopping list itself is the source of truth. Everything is lost on restart, like the database.
 */
@Component
public class ConversationStore {

    private static final int DEFAULT_MAX_MESSAGES = 16;
    private static final int DEFAULT_MAX_CONVERSATIONS = 200;

    private final int maxMessages;
    private final Map<String, List<OllamaMessage>> conversations;
    private final Map<String, Set<String>> pendingConfirmations;

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
        this.pendingConfirmations = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
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

    /**
     * Returns the tools whose confirmation was requested in the previous turn, and forgets them: a confirmation
     * can only be given in the very next message.
     */
    public synchronized Set<String> takePendingConfirmations(String conversationId) {
        Set<String> pending = pendingConfirmations.remove(conversationId);
        return pending == null ? Set.of() : pending;
    }

    public synchronized void setPendingConfirmations(String conversationId, Set<String> tools) {
        if (tools.isEmpty()) {
            pendingConfirmations.remove(conversationId);
        } else {
            pendingConfirmations.put(conversationId, Set.copyOf(tools));
        }
    }

    public synchronized void clear(String conversationId) {
        conversations.remove(conversationId);
        pendingConfirmations.remove(conversationId);
    }
}
