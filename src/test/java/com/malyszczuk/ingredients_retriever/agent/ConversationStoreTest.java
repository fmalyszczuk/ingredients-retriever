package com.malyszczuk.ingredients_retriever.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStoreTest {

    @Test
    void history_isEmpty_forUnknownConversation() {
        assertTrue(new ConversationStore().history("nope").isEmpty());
    }

    @Test
    void append_storesUserAndAssistantMessagesInOrder() {
        ConversationStore store = new ConversationStore();

        store.append("c1", "add eggs", "Added eggs.");
        store.append("c1", "and milk", "Added milk.");

        List<OllamaMessage> history = store.history("c1");
        assertEquals(List.of("user", "assistant", "user", "assistant"), history.stream().map(OllamaMessage::role).toList());
        assertEquals("add eggs", history.get(0).content());
        assertEquals("Added milk.", history.get(3).content());
    }

    @Test
    void append_keepsConversationsSeparate() {
        ConversationStore store = new ConversationStore();

        store.append("a", "hello a", "hi a");
        store.append("b", "hello b", "hi b");

        assertEquals(2, store.history("a").size());
        assertEquals("hello b", store.history("b").getFirst().content());
    }

    @Test
    void append_dropsTheOldestWholeTurns_whenHistoryIsTooLong() {
        ConversationStore store = new ConversationStore(4, 10);

        store.append("c1", "q1", "a1");
        store.append("c1", "q2", "a2");
        store.append("c1", "q3", "a3");

        List<OllamaMessage> history = store.history("c1");
        assertEquals(4, history.size());
        assertEquals("q2", history.getFirst().content());
        assertEquals("user", history.getFirst().role());
        assertEquals("a3", history.getLast().content());
    }

    @Test
    void append_evictsTheLeastRecentlyUsedConversation_whenTooManyExist() {
        ConversationStore store = new ConversationStore(16, 2);

        store.append("old", "q", "a");
        store.append("mid", "q", "a");
        store.history("old"); // reading makes "old" the most recently used
        store.append("new", "q", "a");

        assertEquals(2, store.history("old").size());
        assertTrue(store.history("mid").isEmpty());
        assertEquals(2, store.history("new").size());
    }

    @Test
    void append_storesAnEmptyReply_whenTheModelReturnedNone() {
        ConversationStore store = new ConversationStore();

        store.append("c1", "hi", null);

        assertEquals("", store.history("c1").get(1).content());
    }

    @Test
    void clear_forgetsTheConversation() {
        ConversationStore store = new ConversationStore();
        store.append("c1", "q", "a");

        store.clear("c1");

        assertTrue(store.history("c1").isEmpty());
    }

    @Test
    void history_returnsACopyThatCannotChangeTheStore() {
        ConversationStore store = new ConversationStore();
        store.append("c1", "q", "a");

        assertThrows(UnsupportedOperationException.class, () -> store.history("c1").clear());
        assertEquals(2, store.history("c1").size());
    }

    @Test
    void takePendingConfirmations_returnsThemOnce() {
        ConversationStore store = new ConversationStore();
        store.setPendingConfirmations("c1", java.util.Set.of("clear_shopping_list"));

        assertEquals(java.util.Set.of("clear_shopping_list"), store.takePendingConfirmations("c1"));
        assertTrue(store.takePendingConfirmations("c1").isEmpty());
    }

    @Test
    void setPendingConfirmations_withNoToolsClearsWhatWasPending() {
        ConversationStore store = new ConversationStore();
        store.setPendingConfirmations("c1", java.util.Set.of("clear_shopping_list"));

        store.setPendingConfirmations("c1", java.util.Set.of());

        assertTrue(store.takePendingConfirmations("c1").isEmpty());
    }

    @Test
    void pendingConfirmations_areKeptPerConversation_andForgottenOnClear() {
        ConversationStore store = new ConversationStore();
        store.setPendingConfirmations("a", java.util.Set.of("clear_shopping_list"));
        store.setPendingConfirmations("b", java.util.Set.of("clear_shopping_list"));

        store.clear("a");

        assertTrue(store.takePendingConfirmations("a").isEmpty());
        assertEquals(1, store.takePendingConfirmations("b").size());
    }
}
